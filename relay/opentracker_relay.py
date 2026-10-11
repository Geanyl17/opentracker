#!/usr/bin/env python3
"""OpenTracker relay: lets the OpenTracker app use an AI coding CLI on this computer.

The phone sends prompts over HTTPS. The relay runs each one through Antigravity (agy), Claude Code (claude) or Codex
(codex), signed in with your own account, with every tool turned off, so the model can't read files or run commands
here. Only the Python standard library and openssl are needed.
"""

import argparse
import hashlib
import hmac
import json
import os
import re
import secrets
import shutil
import signal
import socket
import ssl
import subprocess
import sys
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

VERSION = "0.3.0"
DEFAULT_PORT = 8765
MAX_BODY = 8 * 1024 * 1024
RUN_TIMEOUT = 3 * 60
JOB_TIMEOUT = 20 * 60
WARM_MAX_AGE = 10 * 60
JOB_TTL = 60 * 60
MODEL_ID = re.compile(r"^[A-Za-z0-9._-]{1,80}$")
CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

INSTRUCTIONS = """You help the user with their personal budget, using only the information in their message. You \
can't read files, browse the web or run commands, so don't offer to."""

# excludeDefaultComponents drops agy's built-in tools; finish is the one it needs to return structured output.
AGENT = f"""---
name: opentracker
description: Budget assistant for the OpenTracker app. Answers only from the message it's given.
mainAgent: true
subagent: false
hidden: true
excludeDefaultComponents: true
inheritMcp: false
tools: [finish]
---
# OpenTracker

{INSTRUCTIONS}
"""


class RelayError(Exception):
    pass


def config_dir() -> Path:
    return Path(os.environ.get("XDG_CONFIG_HOME") or Path.home() / ".config") / "opentracker-relay"


def data_dir() -> Path:
    return Path(os.environ.get("XDG_DATA_HOME") or Path.home() / ".local" / "share") / "opentracker-relay"


def write_private(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as file:
        file.write(text)


def pairing_code(renew: bool = False) -> str:
    path = config_dir() / "code"
    if path.exists() and not renew:
        return path.read_text().strip()
    raw = "".join(secrets.choice(CODE_ALPHABET) for _ in range(12))
    code = "-".join(raw[i:i + 4] for i in range(0, 12, 4))
    write_private(path, code + "\n")
    return code


def normalize_code(code: str) -> str:
    return re.sub(r"[^A-Z0-9]", "", code.upper())


def certificate() -> tuple[Path, Path, str]:
    cert, key = config_dir() / "cert.pem", config_dir() / "key.pem"
    if not (cert.exists() and key.exists()):
        if not shutil.which("openssl"):
            sys.exit("openssl is needed to create the relay's certificate. Install it and try again.")
        config_dir().mkdir(parents=True, exist_ok=True)
        subprocess.run(
            ["openssl", "req", "-x509", "-newkey", "ec", "-pkeyopt", "ec_paramgen_curve:prime256v1", "-nodes",
             "-keyout", str(key), "-out", str(cert), "-days", "3650", "-subj", "/CN=OpenTracker relay"],
            check=True, capture_output=True,
        )
        key.chmod(0o600)
    der = ssl.PEM_cert_to_DER_cert(cert.read_text())
    return cert, key, hashlib.sha256(der).hexdigest().upper()


def short_fingerprint(fingerprint: str) -> str:
    return ":".join(fingerprint[i:i + 2] for i in range(0, 16, 2))


def addresses(port: int) -> list[str]:
    found = []
    probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        probe.connect(("192.0.2.1", 9))
        found.append(f"{probe.getsockname()[0]}:{port}")
    except OSError:
        pass
    finally:
        probe.close()
    if shutil.which("tailscale"):
        result = subprocess.run(["tailscale", "ip", "-4"], capture_output=True, text=True)
        found += [f"{ip.strip()}:{port} (Tailscale)" for ip in result.stdout.splitlines() if ip.strip()]
    return found or [f"<this computer's IP>:{port}"]


def clean(text: str) -> str:
    """Drops the status lines agy writes into the text when the agent finishes, like {"toolAction": ...}."""
    def is_status(line: str) -> bool:
        try:
            value = json.loads(line)
        except ValueError:
            return False
        return isinstance(value, dict) and bool(value) and set(value) <= {"toolAction", "toolSummary"}

    return "\n".join(line for line in text.splitlines() if not is_status(line.strip())).strip()


def strict_schema(schema):
    """Rewrites a JSON schema for OpenAI's strict mode: every property required, optional ones nullable."""
    if isinstance(schema, list):
        return [strict_schema(item) for item in schema]
    if not isinstance(schema, dict):
        return schema
    result = {key: strict_schema(value) for key, value in schema.items() if key != "properties"}
    if isinstance(schema.get("properties"), dict):
        required = set(schema.get("required", []))
        properties = {}
        for name, prop in schema["properties"].items():
            prop = strict_schema(prop)
            if name not in required and isinstance(prop, dict):
                prop = dict(prop)
                if isinstance(prop.get("type"), str):
                    prop["type"] = [prop["type"], "null"]
                if isinstance(prop.get("enum"), list):
                    prop["enum"] = prop["enum"] + [None]
            properties[name] = prop
        result.update(properties=properties, required=list(properties), additionalProperties=False)
    return result


def kill(process: subprocess.Popen) -> None:
    """Stops a CLI and anything it started. Some CLIs are launchers whose child would otherwise keep running."""
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except OSError:
        pass


def without_nulls(value):
    if isinstance(value, dict):
        return {key: without_nulls(item) for key, item in value.items() if item is not None}
    if isinstance(value, list):
        return [without_nulls(item) for item in value]
    return value


class Cli:
    """A coding-agent CLI that the relay runs prompts through, set up so the model has no tools.

    Each prompt gets its own process. One is started ahead of time, so replies skip the CLI's startup.
    """

    name = ""
    command_name = ""
    fast_model = None
    strong_model = None

    def __init__(self, binary: str, workspace: Path):
        self.binary = binary
        self.workspace = workspace
        workspace.mkdir(parents=True, exist_ok=True)
        (workspace / "schemas").mkdir(exist_ok=True)
        self.lock = threading.Lock()
        self.warm = None
        self.known = None
        self.log = open(data_dir() / "cli.log", "a", buffering=1)
        result = subprocess.run([binary, "--version"], capture_output=True, text=True, timeout=30,
                                stdin=subprocess.DEVNULL)
        self.version = (result.stdout.strip().splitlines() or ["unknown"])[0]

    def models(self) -> list[dict]:
        raise NotImplementedError

    def command(self, model: str | None, schema: Path | None, fast: bool) -> list[str]:
        raise NotImplementedError

    def send(self, process: subprocess.Popen, prompt: str) -> None:
        raise NotImplementedError

    def answer(self, process: subprocess.Popen) -> dict:
        """Reads the reply as {"text": ..., "structured": ...}, or raises RelayError."""
        raise NotImplementedError

    def pick_model(self, requested: str | None, fast: bool) -> str | None:
        """The model the app asked for if this CLI has it, otherwise this CLI's default for the job."""
        if requested in (self.fast_model, self.strong_model):
            return requested
        if requested:
            now = time.monotonic()
            if self.known is None or now - self.known[0] > WARM_MAX_AGE:
                try:
                    self.known = (now, {model["id"] for model in self.models()})
                except (RelayError, OSError, subprocess.SubprocessError):
                    self.known = (now, set())
            if requested in self.known[1]:
                return requested
        return self.fast_model if fast else self.strong_model

    def schema_path(self, schema) -> Path | None:
        if schema is None:
            return None
        text = json.dumps(schema, sort_keys=True)
        path = self.workspace / "schemas" / (hashlib.sha256(text.encode()).hexdigest()[:16] + ".json")
        if not path.exists():
            path.write_text(text)
        return path

    def spawn(self, model: str | None, schema: Path | None, fast: bool) -> subprocess.Popen:
        process = subprocess.Popen(self.command(model, schema, fast), cwd=self.workspace, stdin=subprocess.PIPE,
                                   stdout=subprocess.PIPE, stderr=self.log, text=True, encoding="utf-8", bufsize=1,
                                   start_new_session=True)
        process.started = time.monotonic()
        return process

    def run(self, prompt: str, model: str | None, schema, timeout: float, fast: bool = True,
            started=None) -> dict:
        """Answers one prompt. [started] gets the process, so a long run can be cancelled."""
        key = (self.pick_model(model, fast), self.schema_path(schema), fast)
        process = self.take_warm(key) if started is None else None
        try:
            if process is not None:
                begun = time.monotonic()
                try:
                    return self.converse(process, prompt, timeout)
                except RelayError:
                    # A process that fails right away has usually lost its session while waiting; a fresh one works.
                    self.finish(process)
                    if time.monotonic() - begun > 20:
                        raise
            process = self.spawn(*key)
            if started is not None:
                started(process)
            return self.converse(process, prompt, timeout)
        finally:
            if process is not None:
                self.finish(process)
            if started is None:
                threading.Thread(target=self.prewarm, args=(key,), daemon=True).start()

    def take_warm(self, key):
        with self.lock:
            warm, self.warm = self.warm, None
        if warm is None:
            return None
        warm_key, process = warm
        fresh = time.monotonic() - process.started < WARM_MAX_AGE
        if warm_key == key and fresh and process.poll() is None:
            return process
        self.finish(process)
        return None

    def prewarm(self, key) -> None:
        process = self.spawn(*key)
        with self.lock:
            old, self.warm = self.warm, (key, process)
        if old is not None:
            self.finish(old[1])

    def converse(self, process: subprocess.Popen, prompt: str, timeout: float) -> dict:
        expired = threading.Event()

        def expire():
            expired.set()
            kill(process)

        timer = threading.Timer(timeout, expire)
        timer.start()
        try:
            self.send(process, prompt)
            return self.answer(process)
        except (BrokenPipeError, OSError) as error:
            raise RelayError(f"{self.command_name} stopped unexpectedly ({error}).") from error
        except RelayError:
            if expired.is_set():
                raise RelayError(f"{self.name} took too long to answer.") from None
            raise
        finally:
            timer.cancel()

    def finish(self, process: subprocess.Popen) -> None:
        try:
            process.stdin.close()
        except OSError:
            pass
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            pass
        kill(process)

    def close(self) -> None:
        with self.lock:
            warm, self.warm = self.warm, None
        if warm is not None:
            kill(warm[1])

    def not_signed_in(self) -> RelayError:
        return RelayError(f"{self.command_name} stopped without answering. "
                          f"Run {self.command_name} once on this computer to check that you're signed in.")


class Agy(Cli):
    """The Antigravity CLI, through a custom agent with none of agy's built-in tools."""

    name = "Antigravity"
    command_name = "agy"
    fast_model = "gemini-3.8-flash-low"
    strong_model = "gemini-3.8-flash-high"

    def __init__(self, binary: str, workspace: Path):
        super().__init__(binary, workspace)
        agent = workspace / ".agents" / "agents" / "opentracker.md"
        agent.parent.mkdir(parents=True, exist_ok=True)
        agent.write_text(AGENT)

    def models(self) -> list[dict]:
        result = subprocess.run([self.binary, "models"], cwd=self.workspace, capture_output=True, text=True,
                                timeout=60, stdin=subprocess.DEVNULL)
        models = []
        for line in result.stdout.splitlines():
            parts = line.split("\t")
            if len(parts) == 2 and MODEL_ID.match(parts[0]):
                models.append({"id": parts[0], "name": parts[1].strip()})
        if not models:
            raise RelayError("agy didn't list any models. Run agy once on this computer to sign in.")
        return models

    def command(self, model, schema, fast):
        command = [self.binary, "--agent", "opentracker", "--disable-slash-commands",
                   "--input-format", "stream-json", "--output-format", "stream-json"]
        if model:
            command += ["--model", model]
        if schema is not None:
            command += ["--json-schema", str(schema)]
        return command + ["-p", ""]

    def send(self, process, prompt):
        process.stdin.write(json.dumps({"event": "user", "message": {"role": "user", "content": prompt}}) + "\n")
        process.stdin.flush()

    def answer(self, process):
        for line in process.stdout:
            try:
                event = json.loads(line)
            except ValueError:
                continue
            if event.get("event") != "result":
                continue
            result = event.get("result") or {}
            if result.get("status") != "SUCCESS":
                raise RelayError(result.get("error") or "Antigravity couldn't answer.")
            return {"text": clean(result.get("response") or ""), "structured": result.get("structured_output")}
        raise self.not_signed_in()


class Claude(Cli):
    """Claude Code with every tool turned off, and without the user's settings, hooks, plugins or MCP servers."""

    name = "Claude Code"
    command_name = "claude"
    fast_model = "haiku"
    strong_model = "sonnet"

    def models(self) -> list[dict]:
        return [{"id": "haiku", "name": "Claude Haiku"}, {"id": "sonnet", "name": "Claude Sonnet"},
                {"id": "opus", "name": "Claude Opus"}]

    def command(self, model, schema, fast):
        command = [self.binary, "-p", "--input-format", "stream-json", "--output-format", "stream-json", "--verbose",
                   "--safe-mode", "--strict-mcp-config", "--tools", "", "--disable-slash-commands",
                   "--no-session-persistence", "--system-prompt", INSTRUCTIONS]
        if model:
            command += ["--model", model]
        if schema is not None:
            command += ["--json-schema", schema.read_text()]
        return command

    def send(self, process, prompt):
        process.stdin.write(json.dumps({"type": "user", "message": {"role": "user", "content": prompt}}) + "\n")
        process.stdin.flush()

    def answer(self, process):
        for line in process.stdout:
            try:
                event = json.loads(line)
            except ValueError:
                continue
            if event.get("type") != "result":
                continue
            if event.get("is_error") or event.get("subtype") != "success":
                raise RelayError(event.get("result") or "Claude Code couldn't answer.")
            return {"text": (event.get("result") or "").strip(), "structured": event.get("structured_output")}
        raise self.not_signed_in()


class Codex(Cli):
    """Codex with its shell, web search and other tools turned off, in a read-only sandbox, ignoring the user's config."""

    name = "Codex"
    command_name = "codex"
    TOOL_FEATURES = ["shell_tool", "unified_exec", "view_image", "multi_agent", "goals", "apps", "plugins", "hooks",
                     "image_generation", "browser_use", "computer_use"]

    def models(self) -> list[dict]:
        result = subprocess.run([self.binary, "debug", "models"], cwd=self.workspace, capture_output=True, text=True,
                                timeout=60, stdin=subprocess.DEVNULL)
        try:
            catalog = json.loads(result.stdout).get("models", [])
        except (ValueError, AttributeError):
            raise RelayError("codex didn't list any models.") from None
        listed = sorted((m for m in catalog if m.get("visibility") == "list"), key=lambda m: m.get("priority", 99))
        return [{"id": m["slug"], "name": m.get("display_name") or m["slug"]}
                for m in listed if MODEL_ID.match(str(m.get("slug", "")))]

    def command(self, model, schema, fast):
        command = [self.binary, "exec", "--skip-git-repo-check", "--ephemeral", "--ignore-user-config",
                   "--ignore-rules", "--sandbox", "read-only", "--cd", str(self.workspace),
                   "-c", 'web_search="disabled"', "-c", "developer_instructions=" + json.dumps(INSTRUCTIONS)]
        for feature in self.TOOL_FEATURES:
            command += ["--disable", feature]
        if fast:
            command += ["-c", 'model_reasoning_effort="low"']
        if model:
            command += ["--model", model]
        if schema is not None:
            strict = schema.with_name(schema.stem + ".strict.json")
            if not strict.exists():
                strict.write_text(json.dumps(strict_schema(json.loads(schema.read_text()))))
            command += ["--output-schema", str(strict)]
        return command + ["--output-last-message", str(self.workspace / "replies" / uuid.uuid4().hex), "-"]

    def spawn(self, model, schema, fast):
        (self.workspace / "replies").mkdir(exist_ok=True)
        process = subprocess.Popen(self.command(model, schema, fast), cwd=self.workspace, stdin=subprocess.PIPE,
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8",
                                   start_new_session=True)
        process.started = time.monotonic()
        process.reply = Path(process.args[-2])
        process.structured = schema is not None
        return process

    def send(self, process, prompt):
        process.stdin.write(prompt)
        process.stdin.close()

    def answer(self, process):
        output = process.stdout.read()
        process.wait()
        self.log.write(output)
        lines = output.splitlines()
        try:
            text = process.reply.read_text().strip()
        except OSError:
            text = ""
        finally:
            process.reply.unlink(missing_ok=True)
        if process.returncode != 0 or not text:
            errors = [line.removeprefix("ERROR:").strip() for line in lines if line.startswith("ERROR:")]
            if errors:
                raise RelayError(errors[-1])
            raise self.not_signed_in()
        structured = None
        if process.structured:
            try:
                structured = without_nulls(json.loads(text))
            except ValueError:
                pass
        return {"text": text, "structured": structured}

    def finish(self, process):
        super().finish(process)
        process.reply.unlink(missing_ok=True)


CLIS = {"agy": Agy, "claude": Claude, "codex": Codex}


class Jobs:
    """Long runs, like the deep analysis, that the app starts and then polls."""

    def __init__(self, cli: Cli):
        self.cli = cli
        self.jobs = {}
        self.lock = threading.Lock()

    def start(self, prompt: str, model: str | None, schema) -> str:
        job_id = uuid.uuid4().hex
        job = {"status": "running", "process": None, "finished": None}
        with self.lock:
            self.prune()
            self.jobs[job_id] = job
        threading.Thread(target=self.work, args=(job, prompt, model, schema), daemon=True).start()
        return job_id

    def work(self, job: dict, prompt: str, model: str | None, schema) -> None:
        def started(process):
            job["process"] = process
            if job["status"] == "cancelled":
                kill(process)

        try:
            answer = self.cli.run(prompt, model, schema, JOB_TIMEOUT, fast=False, started=started)
            if job["status"] == "running":
                job.update(status="done", **answer)
        except RelayError as error:
            if job["status"] == "running":
                job.update(status="failed", error=str(error))
        finally:
            job["process"] = None
            job["finished"] = time.monotonic()

    def get(self, job_id: str) -> dict | None:
        with self.lock:
            job = self.jobs.get(job_id)
            return None if job is None else {k: v for k, v in job.items() if k not in ("process", "finished")}

    def cancel(self, job_id: str) -> bool:
        with self.lock:
            job = self.jobs.pop(job_id, None)
        if job is None:
            return False
        job["status"] = "cancelled"
        if job["process"] is not None:
            kill(job["process"])
        return True

    def prune(self) -> None:
        now = time.monotonic()
        for job_id in [k for k, job in self.jobs.items() if job["finished"] and now - job["finished"] > JOB_TTL]:
            del self.jobs[job_id]


class Relay(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address, code: str, cli: Cli):
        super().__init__(address, Handler)
        self.code = normalize_code(code)
        self.cli = cli
        self.jobs = Jobs(cli)
        self.failures = {}
        self.failures_lock = threading.Lock()


class Handler(BaseHTTPRequestHandler):
    server: Relay
    server_version = f"OpenTrackerRelay/{VERSION}"
    timeout = 60

    def do_GET(self):
        if not self.authorized():
            return
        if self.path == "/v1/status":
            cli = self.server.cli
            self.reply(200, {"relay": VERSION, "cli": cli.name, "version": cli.version})
        elif self.path == "/v1/models":
            self.guard(lambda: self.reply(200, {"models": self.server.cli.models()}))
        elif self.path.startswith("/v1/jobs/"):
            job = self.server.jobs.get(self.path.removeprefix("/v1/jobs/"))
            if job is None:
                self.reply(404, {"error": "No such job. The relay may have restarted."})
            else:
                self.reply(200, job)
        else:
            self.reply(404, {"error": "Not found."})

    def do_POST(self):
        if not self.authorized():
            return
        if self.path not in ("/v1/run", "/v1/jobs"):
            return self.reply(404, {"error": "Not found."})
        body = self.body()
        if body is None:
            return
        prompt, model, schema = body.get("prompt"), body.get("model"), body.get("schema")
        if not isinstance(prompt, str) or not prompt.strip():
            return self.reply(400, {"error": "The prompt is empty."})
        if model in ("", None):
            model = None
        elif not isinstance(model, str) or not MODEL_ID.match(model):
            return self.reply(400, {"error": "That isn't a valid model name."})
        if schema is not None and not isinstance(schema, dict):
            return self.reply(400, {"error": "The schema must be a JSON object."})
        if self.path == "/v1/jobs":
            return self.reply(202, {"id": self.server.jobs.start(prompt, model, schema)})
        self.guard(lambda: self.reply(200, self.server.cli.run(prompt, model, schema, RUN_TIMEOUT)))

    def do_DELETE(self):
        if not self.authorized():
            return
        if self.path.startswith("/v1/jobs/") and self.server.jobs.cancel(self.path.removeprefix("/v1/jobs/")):
            self.reply(200, {"cancelled": True})
        else:
            self.reply(404, {"error": "No such job."})

    def guard(self, action):
        try:
            action()
        except RelayError as error:
            self.reply(502, {"error": str(error)})
        except subprocess.TimeoutExpired:
            self.reply(504, {"error": f"{self.server.cli.name} took too long to answer."})

    def authorized(self) -> bool:
        client = self.client_address[0]
        now = time.monotonic()
        with self.server.failures_lock:
            recent = [t for t in self.server.failures.get(client, []) if now - t < 600]
            self.server.failures[client] = recent
            if len(recent) >= 10:
                self.reply(429, {"error": "Too many wrong pairing codes. Try again in a few minutes."})
                return False
        given = normalize_code(self.headers.get("Authorization", "").removeprefix("Bearer "))
        if given and hmac.compare_digest(given, self.server.code):
            return True
        with self.server.failures_lock:
            self.server.failures[client].append(now)
        self.reply(401, {"error": "Wrong pairing code."})
        return False

    def body(self):
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0 or length > MAX_BODY:
            self.reply(413 if length > MAX_BODY else 400, {"error": "The request is empty or too large."})
            return None
        try:
            value = json.loads(self.rfile.read(length))
        except ValueError:
            self.reply(400, {"error": "The request isn't valid JSON."})
            return None
        if not isinstance(value, dict):
            self.reply(400, {"error": "The request must be a JSON object."})
            return None
        return value

    def reply(self, status: int, payload: dict):
        data = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, format, *args):
        print(f"{self.client_address[0]} {format % args}", flush=True)


def install_service(cli: str, binary: str, port: int) -> None:
    unit = Path.home() / ".config" / "systemd" / "user" / "opentracker-relay.service"
    unit.parent.mkdir(parents=True, exist_ok=True)
    unit.write_text(
        "[Unit]\nDescription=OpenTracker relay\nAfter=network-online.target\n\n"
        f"[Service]\nExecStart={sys.executable} {Path(__file__).resolve()} --cli {cli} --path {binary} --port {port}\n"
        "Restart=on-failure\n\n[Install]\nWantedBy=default.target\n"
    )
    subprocess.run(["systemctl", "--user", "daemon-reload"], check=True)
    subprocess.run(["systemctl", "--user", "enable", "--now", "opentracker-relay.service"], check=True)
    print(f"Installed {unit} and started it. It starts again whenever you log in.")
    print("To remove it: systemctl --user disable --now opentracker-relay && rm " + str(unit))


def main() -> None:
    parser = argparse.ArgumentParser(description="Let the OpenTracker app use an AI coding CLI on this computer.")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help=f"port to listen on (default {DEFAULT_PORT})")
    parser.add_argument("--host", default="0.0.0.0", help="address to listen on (default: all)")
    parser.add_argument("--cli", choices=list(CLIS), help="which CLI to use (default: the first one installed, "
                        "in the order agy, claude, codex)")
    parser.add_argument("--path", help="path to the CLI, if it isn't on your PATH")
    parser.add_argument("--pairing", action="store_true", help="show the pairing details and exit")
    parser.add_argument("--new-code", action="store_true", help="make a new pairing code, which unpairs every phone")
    parser.add_argument("--install-service", action="store_true", help="start the relay automatically when you log in")
    args = parser.parse_args()

    name = args.cli or next((cli for cli in CLIS if shutil.which(cli)), None)
    if name is None:
        sys.exit("Couldn't find agy, claude or codex. Install one and sign in, or pass its path with --cli and --path.")
    binary = args.path or shutil.which(name)
    if not binary or not Path(binary).exists():
        sys.exit(f"Couldn't find {name}. Install it, or pass its path with --path.")
    if args.install_service:
        return install_service(name, str(Path(binary).resolve()), args.port)

    code = pairing_code(renew=args.new_code)
    cert, key, fingerprint = certificate()
    found = addresses(args.port)
    pairing = "\n".join(
        ["", "Pair the app in OpenTracker > Settings > AI on your PC:", f"  Address      {found[0]}"]
        + [f"               {address}" for address in found[1:]]
        + [f"  Code         {code}", f"  Certificate  {short_fingerprint(fingerprint)}", ""]
    )
    if args.pairing:
        return print(pairing)

    data_dir().mkdir(parents=True, exist_ok=True)
    cli = CLIS[name](binary, data_dir() / "workspace")
    server = Relay((args.host, args.port), code, cli)
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain(cert, key)
    server.socket = context.wrap_socket(server.socket, server_side=True, do_handshake_on_connect=False)

    print(f"OpenTracker relay {VERSION} is running with {cli.name} ({cli.version}).")
    print(pairing)
    print("Leave it running while you use the assistant. Press Ctrl+C to stop it.", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        cli.close()
        server.server_close()


if __name__ == "__main__":
    main()
