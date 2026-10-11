package io.github.geanyl17.opentracker.ai

import io.github.geanyl17.opentracker.data.Amounts
import io.github.geanyl17.opentracker.data.Ledger
import io.github.geanyl17.opentracker.data.SettingsStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * A conversation with the AI on the user's PC, through the relay. Each message is a single request carrying the rules, a fresh
 * snapshot of the data and the recent conversation; the answer's actions are applied here, on the phone.
 */
class Assistant(
    private val relay: RelayClient,
    private val ledger: Ledger,
    private val settings: SettingsStore,
) {
    data class Reply(val text: String, val actions: List<AssistantAction>, val problems: List<String>)

    private data class Exchange(val user: String, val reply: String, val done: List<String>)

    private val tools = AssistantTools(ledger, settings)
    private val history = ArrayDeque<Exchange>()

    suspend fun send(text: String): Reply {
        val context = FinanceContext.load(ledger, settings)
        val answer = readAnswer(relay.run(prompt(context, text), context.settings.assistantModel, AssistantTools.schema))
        val actions = mutableListOf<AssistantAction>()
        val problems = mutableListOf<String>()
        answer.actions.forEach { action ->
            when (val outcome = tools.apply(action, context)) {
                is AssistantTools.Outcome.Applied -> actions += outcome.action
                is AssistantTools.Outcome.Rejected -> problems += outcome.reason
            }
        }
        if (answer.reply.isBlank() && actions.isEmpty()) throw RelayException("The AI didn't answer. Try asking another way.")
        history.addLast(Exchange(text, answer.reply, actions.map(::summary) + problems.map { "Couldn't: $it" }))
        while (history.size > MAX_EXCHANGES) history.removeFirst()
        return Reply(answer.reply, actions, problems)
    }

    suspend fun undo(action: AssistantAction) = tools.undo(action)

    fun reset() = history.clear()

    private fun prompt(context: FinanceContext, text: String) = buildString {
        appendLine(context.assistantRules())
        appendLine()
        appendLine(context.overview())
        appendLine(context.recentTransactions())
        if (history.isNotEmpty()) {
            appendLine("Conversation so far, oldest first:")
            history.forEach { exchange ->
                appendLine("User: ${exchange.user}")
                appendLine("You: ${exchange.reply}")
                exchange.done.forEach { appendLine("(App: $it)") }
            }
            appendLine()
        }
        append("The user says: $text")
    }

    private fun summary(action: AssistantAction): String = when (action) {
        is AssistantAction.Added -> with(action.entry) {
            "recorded transaction $id, ${kind.label} ${Amounts.plain(amount)} in ${action.category ?: "no category"} on $date"
        }
        is AssistantAction.Updated -> "updated transaction ${action.after.id}"
        is AssistantAction.Deleted -> "deleted transaction ${action.entry.id}"
        is AssistantAction.BudgetChanged ->
            "set the ${action.category?.name ?: "overall monthly"} budget to ${action.budget?.let(Amounts::plain) ?: "none"}"
    }

    private companion object {
        const val MAX_EXCHANGES = 8
    }
}

data class Answer(val reply: String, val actions: List<JsonObject>)

/** Reads the structured answer, or JSON in the text if the CLI didn't return one, or else treats the text as the reply. */
internal fun readAnswer(result: RelayResult): Answer {
    val json = result.structured
        ?: runCatching { Json.parseToJsonElement(result.text.substringAfter("```json").substringBefore("```").trim()).jsonObject }.getOrNull()
        ?: return Answer(result.text.trim(), emptyList())
    return Answer(
        reply = json.string("reply")?.trim().orEmpty(),
        actions = (json["actions"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject },
    )
}
