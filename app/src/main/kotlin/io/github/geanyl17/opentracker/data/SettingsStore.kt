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
    /** Empty means the default of whichever CLI the relay runs. */
    val assistantModel: String = "",
    val analysisModel: String = "",
    val speakReplies: Boolean = true,
    val speechLanguage: String = "",
    val pendingAnalysis: String? = null,
    val pendingAnalysisSince: Long? = null,
)

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
        val assistantModel = stringPreferencesKey("assistant_model")
        val analysisModel = stringPreferencesKey("analysis_model")
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
            assistantModel = model(this[Keys.assistantModel], "gemini-3.8-flash-low"),
            analysisModel = model(this[Keys.analysisModel], "gemini-3.8-flash-high"),
            speakReplies = this[Keys.speakReplies] ?: defaults.speakReplies,
            speechLanguage = this[Keys.speechLanguage] ?: defaults.speechLanguage,
            pendingAnalysis = this[Keys.pendingAnalysis],
            pendingAnalysisSince = this[Keys.pendingAnalysisSince],
        )
    }

    // Version 0.1.0 saved its Antigravity defaults. The relay picks those same models by default, so they read as empty.
    private fun model(saved: String?, oldDefault: String): String = saved?.takeUnless { it == oldDefault }.orEmpty()

    private fun Settings.writeTo(prefs: MutablePreferences) {
        prefs[Keys.currency] = currency
        prefs.setOrRemove(Keys.monthlyBudget, monthlyBudget)
        prefs[Keys.theme] = theme.name
        prefs[Keys.dynamicColor] = dynamicColor
        prefs[Keys.assistantModel] = assistantModel
        prefs[Keys.analysisModel] = analysisModel
        prefs[Keys.speakReplies] = speakReplies
        prefs[Keys.speechLanguage] = speechLanguage
        prefs.setOrRemove(Keys.pendingAnalysis, pendingAnalysis)
        prefs.setOrRemove(Keys.pendingAnalysisSince, pendingAnalysisSince)
    }

    private fun <T> MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else set(key, value)
    }
}

/** Where the relay on the user's computer is, and how to trust it. */
data class Pairing(val address: String, val code: String, val fingerprint: String)

/** The pairing lives in its own file, which backups leave out (see res/xml/data_extraction_rules.xml). */
class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("secrets", Context.MODE_PRIVATE)
    private val current = MutableStateFlow(read())

    val pairing: StateFlow<Pairing?> = current.asStateFlow()

    fun pair(pairing: Pairing?) {
        prefs.edit {
            clear()
            if (pairing != null) {
                putString(ADDRESS, pairing.address)
                putString(CODE, pairing.code)
                putString(FINGERPRINT, pairing.fingerprint)
            }
        }
        current.value = pairing
    }

    private fun read(): Pairing? = Pairing(
        address = prefs.getString(ADDRESS, null) ?: return null,
        code = prefs.getString(CODE, null) ?: return null,
        fingerprint = prefs.getString(FINGERPRINT, null) ?: return null,
    )

    private companion object {
        const val ADDRESS = "relay_address"
        const val CODE = "relay_code"
        const val FINGERPRINT = "relay_fingerprint"
    }
}
