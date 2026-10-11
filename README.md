# OpenTracker

**A simple, open-source budget tracker for Android that you can talk to.**

Log what you spend by voice or by hand, see where your money goes each month, and ask about it. A Gemini-powered assistant listens and answers out loud, and Google's Antigravity agent can study all your transactions and write a full spending report.

- **Talk to it.** Say "spent 250 on lunch" and it's recorded. Ask "how am I doing this month?" or "where can I cut back?" and hear the answer. Anything it changes comes with an Undo button.
- **Deep analysis with Antigravity.** The Antigravity agent analyzes your transactions with code, in a sandbox with no internet access, and writes a report: trends, recurring payments, unusual spending and ways to save.
- **Budgets that make sense.** A monthly budget and per-category budgets, what's left to spend per day, and where the month is heading.
- **Yours.** No account, no ads, no tracking. Your data stays on your phone, and the AI features only run when you use them, with your own API key.
- **Free software,** licensed GPL-3.0-or-later.

<p>
  <img src="docs/screenshots/home.png" width="200" alt="Home: spending this month against the budget, by category">
  <img src="docs/screenshots/transactions.png" width="200" alt="Transactions grouped by day, with search and filters">
  <img src="docs/screenshots/assistant.png" width="200" alt="The assistant, with suggested questions and deep analysis">
  <img src="docs/screenshots/home-dark.png" width="200" alt="Home in the dark theme">
</p>

The screenshots use demo data.

## Install

OpenTracker runs on Android 8.0 and newer. Build it yourself (see [Building](#building)), or download the debug APK from the latest successful run under **Actions** on GitHub and open it on your phone.

## Connect Gemini and Antigravity

The tracking works without any of this. To turn on the assistant and deep analysis:

1. Create an API key in [Google AI Studio](https://aistudio.google.com/apikey).
2. In OpenTracker, open **Settings**, tap **Gemini API key** and paste it.
3. Go to the **Assistant** tab and tap the mic, or tap the mic on the home screen.

The assistant uses the `gemini-3.8-flash` model and deep analysis uses the `antigravity-preview-09-2026` agent, both through the Gemini [Interactions API](https://ai.google.dev/gemini-api/docs/interactions). When Google releases newer versions, you can change either name in Settings.

Long-press the app icon for shortcuts that go straight to voice input or a new transaction.

### What gets sent to Google

- **Assistant:** each message sends your question with a summary of your data: categories and budgets, monthly totals for the past year, and the transactions of the last three months. Requests use `store: false`, so the conversation isn't kept as an interaction on Google's side.
- **Deep analysis:** sends all your transactions as a CSV file into an Antigravity sandbox with network access turned off. Background runs have to be stored by Google while they work, so the app deletes the run once it has the report. Each run is capped at 400,000 tokens.
- **Voice:** speech is turned into text by your phone's speech recognizer (on most phones, Google's), and replies are read aloud by the phone's text-to-speech.
- **API key:** stored only on your phone and left out of backups.

On the free tier, Google may use what you send to improve its products. See the [Gemini API terms](https://ai.google.dev/gemini-api/terms) and [pricing](https://ai.google.dev/gemini-api/docs/pricing).

## Backups

**Settings → Export to CSV** saves every transaction as a spreadsheet file (`date,type,amount,category,note`). **Import from CSV** reads the same format back, skips transactions that are already there, and creates any missing categories.

## Building

You need JDK 17 or newer and the Android SDK. [Android Studio](https://developer.android.com/studio) includes both.

```bash
./gradlew :app:installDebug   # build and install on a connected phone or emulator
./gradlew check               # run tests and lint
```

Each CI run on GitHub also builds a debug APK, which you can download from the run's page under **Actions**.

## How it's built

Kotlin, Jetpack Compose with Material 3, Room and DataStore. There's no SDK for the Gemini API: it's called over HTTPS with a small client.

| Folder | What's in it |
| --- | --- |
| `data/` | The Room database, settings, money formatting, CSV import and export, monthly summaries |
| `ai/` | The Interactions API client, the assistant and its tools, the Antigravity analyst |
| `voice/` | Speech recognition and text-to-speech |
| `ui/` | The Compose screens |

All under `app/src/main/kotlin/io/github/geanyl17/opentracker/`.

## License

OpenTracker is free software: you can redistribute it and/or modify it under the terms of the [GNU General Public License](LICENSE), version 3 or (at your option) any later version.

Icons are [Material Symbols](https://github.com/google/material-design-icons) by Google, under the Apache License 2.0.
