package io.github.geanyl17.opentracker.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface VoiceState {
    data object Idle : VoiceState
    data class Listening(val partial: String = "", val level: Float = 0f) : VoiceState
    data class Error(val message: String) : VoiceState
}

/** Speech-to-text with the phone's speech recognizer. Must be used from the main thread. */
class SpeechInput(private val context: Context) {
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(language: String, onResult: (String) -> Unit) {
        release()
        if (!isAvailable) {
            _state.value = VoiceState.Error("Speech recognition isn't available on this phone. Install or enable the Google app.")
            return
        }
        val speech = SpeechRecognizer.createSpeechRecognizer(context)
        speech.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.value = VoiceState.Listening()
                }

                override fun onRmsChanged(rmsdB: Float) {
                    val current = _state.value as? VoiceState.Listening ?: return
                    _state.value = current.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults.firstResult() ?: return
                    val current = _state.value as? VoiceState.Listening ?: return
                    _state.value = current.copy(partial = text)
                }

                override fun onResults(results: Bundle?) {
                    val text = results.firstResult()
                    release()
                    if (text.isNullOrBlank()) {
                        _state.value = VoiceState.Error("Didn't catch that. Try again.")
                    } else {
                        onResult(text)
                    }
                }

                override fun onError(error: Int) {
                    release()
                    _state.value = message(error)?.let(VoiceState::Error) ?: VoiceState.Idle
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            },
        )
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        if (language.isNotBlank()) intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        recognizer = speech
        _state.value = VoiceState.Listening()
        speech.startListening(intent)
    }

    /** Stops listening and delivers what was heard so far, even if the recognizer never answers. */
    fun stop(onResult: (String) -> Unit) {
        val speech = recognizer ?: return
        speech.stopListening()
        handler.postDelayed({
            if (recognizer !== speech) return@postDelayed
            val heard = (_state.value as? VoiceState.Listening)?.partial
            release()
            if (!heard.isNullOrBlank()) onResult(heard)
        }, STOP_TIMEOUT_MS)
    }

    fun cancel() {
        release()
        _state.value = VoiceState.Idle
    }

    fun clearError() {
        if (_state.value is VoiceState.Error) _state.value = VoiceState.Idle
    }

    private fun release() {
        recognizer?.destroy()
        recognizer = null
        if (_state.value is VoiceState.Listening) _state.value = VoiceState.Idle
    }

    private fun Bundle?.firstResult(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    private fun message(error: Int): String? = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that. Try again."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition needs a connection."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "OpenTracker needs microphone access for voice input."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The speech recognizer is busy. Try again in a moment."
        SpeechRecognizer.ERROR_CLIENT -> null
        else -> "Voice input stopped (error $error)."
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 3_000L
    }
}
