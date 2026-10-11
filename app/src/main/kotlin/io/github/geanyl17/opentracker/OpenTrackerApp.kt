package io.github.geanyl17.opentracker

import android.app.Application
import android.content.Context
import io.github.geanyl17.opentracker.ai.Analyst
import io.github.geanyl17.opentracker.ai.GeminiClient
import io.github.geanyl17.opentracker.data.AppDatabase
import io.github.geanyl17.opentracker.data.Ledger
import io.github.geanyl17.opentracker.data.SecretStore
import io.github.geanyl17.opentracker.data.SettingsStore

class OpenTrackerApp : Application() {
    val container by lazy { AppContainer(this) }
}

class AppContainer(context: Context) {
    val context: Context = context.applicationContext
    val ledger = Ledger(AppDatabase.build(this.context))
    val settings = SettingsStore(this.context)
    val secrets = SecretStore(this.context)
    val gemini = GeminiClient { secrets.apiKey.value }
    val analyst = Analyst(gemini, ledger, settings)
}
