package io.github.geanyl17.opentracker.ai

import io.github.geanyl17.opentracker.data.Ledger
import io.github.geanyl17.opentracker.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * A conversation with Gemini that can read and change the ledger through function calls.
 *
 * Requests are stateless (store = false), so Google keeps nothing between turns: the history is sent each time,
 * including the model's thought steps, which have to come back unchanged for function calling to work.
 */
class Assistant(
    private val gemini: GeminiClient,
    private val ledger: Ledger,
    private val settings: SettingsStore,
) {
    data class Reply(val text: String, val actions: List<AssistantAction>, val error: String? = null)

    private val tools = AssistantTools(ledger, settings)
    private val history = mutableListOf<JsonObject>()

    suspend fun send(text: String): Reply {
        val start = history.size
        val actions = mutableListOf<AssistantAction>()
        history += buildJsonObject {
            put("type", "user_input")
            putJsonArray("content") {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", text)
                    },
                )
            }
        }
        try {
            repeat(MAX_ROUNDS) {
                val context = FinanceContext.load(ledger, settings)
                val steps = gemini.create(request(context)).steps
                history += steps
                val calls = steps.functionCalls()
                if (calls.isEmpty()) {
                    val reply = steps.outputText()
                    if (reply.isBlank() && actions.isEmpty()) throw GeminiException("Gemini didn't answer. Try asking another way.")
                    trim()
                    return Reply(reply, actions)
                }
                calls.forEach { call ->
                    val outcome = tools.run(call, context)
                    outcome.action?.let(actions::add)
                    history += buildJsonObject {
                        put("type", "function_result")
                        put("name", call.name)
                        put("call_id", call.id)
                        putJsonArray("result") {
                            add(
                                buildJsonObject {
                                    put("type", "text")
                                    put("text", outcome.result.toString())
                                },
                            )
                        }
                    }
                }
            }
            throw GeminiException("The assistant got stuck. Try asking another way.")
        } catch (e: Exception) {
            history.subList(start, history.size).clear()
            if (e is CancellationException || actions.isEmpty()) throw e
            return Reply("", actions, e.message)
        }
    }

    suspend fun undo(action: AssistantAction) = tools.undo(action)

    fun reset() = history.clear()

    private fun request(context: FinanceContext) = buildJsonObject {
        put("model", context.settings.model)
        put("store", false)
        put("system_instruction", context.systemInstruction())
        put("input", JsonArray(history))
        put("tools", tools.declarations(context))
        putJsonObject("generation_config") { put("thinking_level", "low") }
    }

    /** Keeps the last few exchanges, cutting only at the start of one. */
    private fun trim() {
        val starts = history.indices.filter { history[it].type == "user_input" }
        if (starts.size > MAX_EXCHANGES) history.subList(0, starts[starts.size - MAX_EXCHANGES]).clear()
    }

    private companion object {
        const val MAX_ROUNDS = 6
        const val MAX_EXCHANGES = 12
    }
}
