package io.github.geanyl17.opentracker.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Reads replies aloud with the phone's text-to-speech engine, which starts on first use. */
class Speaker(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<String, String>? = null

    fun speak(text: String, language: String) {
        if (ready) {
            say(text, language)
            return
        }
        pending = text to language
        if (tts == null) {
            tts = TextToSpeech(context) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) pending?.let { (text, language) -> say(text, language) }
                pending = null
            }
        }
    }

    fun stop() {
        pending = null
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun say(text: String, language: String) {
        val engine = tts ?: return
        val locale = if (language.isBlank()) Locale.getDefault() else Locale.forLanguageTag(language)
        if (engine.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) engine.language = locale
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply")
    }
}
