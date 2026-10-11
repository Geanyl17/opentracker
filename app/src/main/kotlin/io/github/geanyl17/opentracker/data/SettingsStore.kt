package io.github.geanyl17.opentracker.data

import android.content.Context
import androidx.core.content.edit
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val currency: String = Money.defaultCurrencyCode(),
    val monthlyBudget: Long? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val model: String = DEFAULT_MODEL,
    val agent: String = DEFAULT_AGENT,
    val speakReplies: Boolean = true,
    val speechLanguage: String = "",
    val pendingAnalysis: String? = null,
    val pendingAnalysisSince: Long? = null,
) {
    companion object {
        const val DEFAULT_MODEL = "gemini-3.8-flash"
        const val DEFAULT_AGENT = "antigravity-preview-09-2026"
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { prefs -> transform(prefs.toSettings()).writeTo(prefs) }
    }

    private object Keys {
        val currency = stringPreferencesKey("currency")
        val monthlyBudget = longPreferencesKey("monthly_budget")
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val model = stringPreferencesKey("model")
        val agent = stringPreferencesKey("agent")
        val speakReplies = booleanPreferencesKey("speak_replies")
        val speechLanguage = stringPreferencesKey("speech_language")
        val pendingAnalysis = stringPreferencesKey("pending_analysis")
        val pendingAnalysisSince = longPreferencesKey("pending_analysis_since")
    }

    private fun Preferences.toSettings(): Settings {
        val defaults = Settings()
        return Settings(
            currency = this[Keys.currency] ?: defaults.currency,
            monthlyBudget = this[Keys.monthlyBudget],
            theme = this[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: defaults.theme,
            dynamicColor = this[Keys.dynamicColor] ?: defaults.dynamicColor,
            model = this[Keys.model] ?: defaults.model,
            agent = this[Keys.agent] ?: defaults.agent,
            speakReplies = this[Keys.speakReplies] ?: defaults.speakReplies,
            speechLanguage = this[Keys.speechLanguage] ?: defaults.speechLanguage,
            pendingAnalysis = this[Keys.pendingAnalysis],
            pendingAnalysisSince = this[Keys.pendingAnalysisSince],
        )
    }

    private fun Settings.writeTo(prefs: MutablePreferences) {
        prefs[Keys.currency] = currency
        prefs.setOrRemove(Keys.monthlyBudget, monthlyBudget)
        prefs[Keys.theme] = theme.name
        prefs[Keys.dynamicColor] = dynamicColor
        prefs[Keys.model] = model
        prefs[Keys.agent] = agent
        prefs[Keys.speakReplies] = speakReplies
        prefs[Keys.speechLanguage] = speechLanguage
        prefs.setOrRemove(Keys.pendingAnalysis, pendingAnalysis)
        prefs.setOrRemove(Keys.pendingAnalysisSince, pendingAnalysisSince)
    }

    private fun <T> MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else set(key, value)
    }
}

/** The API key lives in its own file, which backups leave out (see res/xml/data_extraction_rules.xml). */
class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("secrets", Context.MODE_PRIVATE)
    private val key = MutableStateFlow(prefs.getString(API_KEY, null))

    val apiKey: StateFlow<String?> = key.asStateFlow()

    fun setApiKey(value: String?) {
        val cleaned = value?.trim()?.ifEmpty { null }
        prefs.edit { if (cleaned == null) remove(API_KEY) else putString(API_KEY, cleaned) }
        key.value = cleaned
    }

    private companion object {
        const val API_KEY = "gemini_api_key"
    }
}
