package io.github.geanyl17.opentracker.ai

import android.annotation.SuppressLint
import io.github.geanyl17.opentracker.data.Pairing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.X509TrustManager

class RelayException(message: String, val status: Int = 0) : Exception(message)

data class RelayResult(val text: String, val structured: JsonObject?)

data class Model(val id: String, val name: String)

sealed interface Job {
    data object Running : Job
    data class Done(val text: String) : Job
    data class Failed(val message: String) : Job
}

/**
 * Talks to the OpenTracker relay on the user's computer (relay/opentracker_relay.py), which runs prompts through
 * the Antigravity CLI. The relay has a self-signed certificate, so the one seen when pairing is pinned.
 */
class RelayClient(private val pairing: () -> Pairing?) {
    suspend fun run(prompt: String, model: String, schema: JsonObject?): RelayResult {
        val body = buildJsonObject {
            put("prompt", prompt)
            put("model", model)
            if (schema != null) put("schema", schema)
        }
        val response = call(paired(), "POST", "/v1/run", body, readTimeout = 200_000)
        return RelayResult(response.string("text").orEmpty(), response["structured"] as? JsonObject)
    }

    suspend fun startJob(prompt: String, model: String): String {
        val body = buildJsonObject {
            put("prompt", prompt)
            put("model", model)
        }
        return call(paired(), "POST", "/v1/jobs", body).string("id")
            ?: throw RelayException("The relay didn't start the analysis.")
    }

    suspend fun job(id: String): Job {
        val response = call(paired(), "GET", "/v1/jobs/$id")
        return when (response.string("status")) {
            "done" -> Job.Done(response.string("text").orEmpty())
            "failed" -> Job.Failed(response.string("error") ?: "Antigravity couldn't finish the analysis.")
            "cancelled" -> Job.Failed("The analysis was cancelled.")
            else -> Job.Running
        }
    }

    suspend fun cancelJob(id: String) {
        call(paired(), "DELETE", "/v1/jobs/$id")
    }

    suspend fun models(): List<Model> =
        (call(paired(), "GET", "/v1/models")["models"] as? JsonArray).orEmpty().mapNotNull { item ->
            val model = item as? JsonObject ?: return@mapNotNull null
            Model(model.string("id") ?: return@mapNotNull null, model.string("name").orEmpty())
        }

    /** Checks the address and code, and trusts the certificate the relay presents from now on. */
    suspend fun pair(address: String, code: String): Pairing {
        val target = Pairing(
            address = normalizeAddress(address) ?: throw RelayException("Enter an address like 192.168.1.20:8765."),
            code = code.trim().uppercase(),
            fingerprint = "",
        )
        var seen = ""
        call(target, "GET", "/v1/status", onCertificate = { seen = it })
        if (seen.isEmpty()) throw RelayException("Couldn't read the relay's certificate. Try again.")
        return target.copy(fingerprint = seen)
    }

    private fun paired(): Pairing = pairing() ?: throw RelayException("Pair OpenTracker with your PC in Settings first.")

    private suspend fun call(
        target: Pairing,
        method: String,
        path: String,
        body: JsonObject? = null,
        readTimeout: Int = 30_000,
        onCertificate: (String) -> Unit = {},
    ): JsonObject = withContext(Dispatchers.IO) {
        val pin = target.fingerprint.ifEmpty { null }
        var seen: String? = null
        val trust = PinningTrustManager(pin) {
            seen = it
            onCertificate(it)
        }
        val connection = URL("https://${target.address}$path").openConnection() as HttpsURLConnection
        try {
            connection.sslSocketFactory = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }.socketFactory
            connection.hostnameVerifier = HostnameVerifier { _, session ->
                val certificate = session.peerCertificates.firstOrNull() as? X509Certificate
                certificate != null && fingerprint(certificate) == (pin ?: seen)
            }
            connection.requestMethod = method
            connection.connectTimeout = 8_000
            connection.readTimeout = readTimeout
            connection.setRequestProperty("Authorization", "Bearer ${target.code}")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().encodeToByteArray()) }
            }
            val status = connection.responseCode
            val text = (if (status < 400) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
            if (status >= 400) throw RelayException(describe(status, json?.string("error")), status)
            json ?: throw RelayException("The relay sent a response the app couldn't read.")
        } catch (e: RelayException) {
            throw e
        } catch (e: SSLException) {
            if (e.cause is CertificateException || e.cause?.cause is CertificateException) {
                throw RelayException("Your PC's relay has a different certificate than when you paired. Pair again in Settings.")
            }
            throw RelayException("Couldn't set up a secure connection to your PC (${e.message}).")
        } catch (e: SocketTimeoutException) {
            val connecting = e.message.orEmpty().contains("connect", ignoreCase = true)
            throw RelayException(if (connecting) unreachable(target) else "Antigravity took too long to answer. Try again.")
        } catch (e: IOException) {
            throw RelayException(
                when {
                    e is ConnectException || e is NoRouteToHostException || e is UnknownHostException -> unreachable(target)
                    e.message.orEmpty().contains("EPERM") || e.message.orEmpty().contains("not permitted") ->
                        "OpenTracker isn't allowed to use your local network. Allow it in Android Settings > Apps > OpenTracker > Permissions."
                    else -> "Couldn't talk to your PC (${e.message})."
                },
            )
        } catch (e: SerializationException) {
            throw RelayException("The relay sent a response the app couldn't read.")
        } finally {
            connection.disconnect()
        }
    }

    private fun unreachable(target: Pairing): String {
        val host = target.address.substringBeforeLast(':')
        val parts = host.split('.')
        val tailscale = host.endsWith(".ts.net") || (parts.size == 4 && parts[0] == "100" && parts[1].toIntOrNull() in 64..127)
        return if (tailscale) {
            "Can't reach your PC at ${target.address}. Check that Tailscale is on, on this phone and on your PC, and that the relay is running."
        } else {
            "Can't reach your PC at ${target.address}. Check that the relay is running and that your phone is on the same network."
        }
    }

    private fun describe(status: Int, error: String?): String = when (status) {
        401 -> "Your PC didn't accept the pairing code. Pair again in Settings."
        404 -> error ?: "The relay doesn't know that request. Update the relay on your PC."
        else -> error ?: "The relay returned an error ($status)."
    }

    companion object {
        const val DEFAULT_PORT = 8765

        /** "192.168.1.20", "192.168.1.20:8765" or "https://192.168.1.20:8765/" all become "192.168.1.20:8765". */
        fun normalizeAddress(input: String): String? {
            val address = input.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
            if (address.isEmpty() || address.any { it.isWhitespace() || it == '/' }) return null
            val parts = address.split(':')
            return when {
                parts.size == 1 -> "$address:$DEFAULT_PORT"
                parts.size == 2 && parts[0].isNotEmpty() && parts[1].toIntOrNull() in 1..65_535 -> address
                else -> null
            }
        }

        fun fingerprint(certificate: X509Certificate): String =
            MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString("") { "%02X".format(it) }

        /** The first eight bytes, as the relay prints them, to compare by eye. */
        fun shortFingerprint(fingerprint: String): String = fingerprint.take(16).chunked(2).joinToString(":")
    }
}

/** Trusts exactly one certificate, the relay's self-signed one, instead of certificate authorities. */
@SuppressLint("CustomX509TrustManager")
private class PinningTrustManager(private val pin: String?, private val seen: (String) -> Unit) : X509TrustManager {
    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        val certificate = chain.firstOrNull() ?: throw CertificateException("The relay sent no certificate.")
        val fingerprint = RelayClient.fingerprint(certificate)
        seen(fingerprint)
        if (pin != null && fingerprint != pin) throw CertificateException("The relay's certificate changed.")
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
        throw CertificateException("Client certificates aren't used.")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
