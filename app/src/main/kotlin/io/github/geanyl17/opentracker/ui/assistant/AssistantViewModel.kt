package io.github.geanyl17.opentracker.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.geanyl17.opentracker.AppContainer
import io.github.geanyl17.opentracker.ai.Assistant
import io.github.geanyl17.opentracker.ai.AssistantAction
import io.github.geanyl17.opentracker.data.Settings
import io.github.geanyl17.opentracker.voice.SpeechInput
import io.github.geanyl17.opentracker.voice.Speaker
import io.github.geanyl17.opentracker.voice.VoiceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ChatMessage {
    val id: Long

    data class User(override val id: Long, val text: String) : ChatMessage
    data class Reply(override val id: Long, val text: String, val actions: List<ActionItem>, val error: String?) : ChatMessage
    data class Failure(override val id: Long, val text: String, val retry: String) : ChatMessage
}

data class ActionItem(val action: AssistantAction, val undone: Boolean = false)

class AssistantViewModel(container: AppContainer) : ViewModel() {
    private val assistant = Assistant(container.gemini, container.ledger, container.settings)
    private val speech = SpeechInput(container.context)
    private val speaker = Speaker(container.context)
    private var nextId = 0L

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val voice: StateFlow<VoiceState> = speech.state
    val apiKey: StateFlow<String?> = container.secrets.apiKey
    val settings: StateFlow<Settings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || _busy.value) return
        speaker.stop()
        add(ChatMessage.User(nextId++, message))
        _busy.value = true
        viewModelScope.launch {
            try {
                val reply = assistant.send(message)
                add(ChatMessage.Reply(nextId++, reply.text, reply.actions.map(::ActionItem), reply.error))
                if (settings.value.speakReplies && reply.text.isNotBlank()) {
                    speaker.speak(reply.text, settings.value.speechLanguage)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                add(ChatMessage.Failure(nextId++, e.message ?: "Something went wrong.", retry = message))
            } finally {
                _busy.value = false
            }
        }
    }

    fun retry(failure: ChatMessage.Failure) {
        _messages.update { list ->
            val index = list.indexOf(failure)
            if (index > 0 && list[index - 1] is ChatMessage.User) list - setOf(failure, list[index - 1]) else list - failure
        }
        send(failure.retry)
    }

    fun undo(reply: ChatMessage.Reply, index: Int) {
        val item = reply.actions.getOrNull(index)?.takeUnless { it.undone } ?: return
        viewModelScope.launch {
            if (runCatching { assistant.undo(item.action) }.isFailure) return@launch
            _messages.update { list ->
                list.map { message ->
                    if (message.id == reply.id && message is ChatMessage.Reply) {
                        message.copy(actions = message.actions.mapIndexed { i, a -> if (i == index) a.copy(undone = true) else a })
                    } else {
                        message
                    }
                }
            }
        }
    }

    fun listen() {
        speaker.stop()
        speech.start(settings.value.speechLanguage, ::send)
    }

    fun stopListening() = speech.stop(::send)

    fun cancelListening() = speech.cancel()

    fun clearVoiceError() = speech.clearError()

    fun newChat() {
        speaker.stop()
        assistant.reset()
        _messages.value = emptyList()
    }

    private fun add(message: ChatMessage) {
        _messages.update { it + message }
    }

    override fun onCleared() {
        speech.cancel()
        speaker.shutdown()
    }
}
