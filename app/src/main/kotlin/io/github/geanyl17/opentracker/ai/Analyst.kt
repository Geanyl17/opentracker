package io.github.geanyl17.opentracker.ai

import io.github.geanyl17.opentracker.data.Amounts
import io.github.geanyl17.opentracker.data.Csv
import io.github.geanyl17.opentracker.data.Ledger
import io.github.geanyl17.opentracker.data.Money
import io.github.geanyl17.opentracker.data.Report
import io.github.geanyl17.opentracker.data.SettingsStore
import io.github.geanyl17.opentracker.data.TransactionsCsv
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate

/**
 * Runs Google's Antigravity agent on the whole ledger. The agent works in a sandbox with the data as CSV files
 * and no internet access, runs in the background on Google's side, and is polled until it's done.
 */
class Analyst(
    private val gemini: GeminiClient,
    private val ledger: Ledger,
    private val settings: SettingsStore,
) {
    sealed interface Status {
        data object Running : Status
        data object Done : Status
        data class Failed(val message: String) : Status
    }

    suspend fun start() {
        val current = settings.current()
        val entries = ledger.allEntries()
        if (entries.isEmpty()) throw GeminiException("Add some transactions first.")
        val categories = ledger.allCategories()
        val categoriesCsv = Csv.write(
            listOf(listOf("category", "type", "monthly_budget")) +
                categories.map { listOf(it.name, it.kind.label, it.budget?.let(Amounts::plain).orEmpty()) },
        )
        val body = buildJsonObject {
            put("agent", current.agent)
            put("input", prompt(current.currency, current.monthlyBudget, LocalDate.now()))
            put("background", true)
            putJsonObject("agent_config") {
                put("type", "antigravity")
                put("max_total_tokens", TOKEN_BUDGET)
            }
            putJsonObject("environment") {
                put("type", "remote")
                put("network", "disabled")
                putJsonArray("sources") {
                    addJsonObject {
                        put("type", "inline")
                        put("target", "/workspace/transactions.csv")
                        put("content", TransactionsCsv.export(entries, categories))
                    }
                    addJsonObject {
                        put("type", "inline")
                        put("target", "/workspace/categories.csv")
                        put("content", categoriesCsv)
                    }
                }
            }
        }
        val id = gemini.create(body).string("id") ?: throw GeminiException("Google didn't start the analysis.")
        settings.update { it.copy(pendingAnalysis = id, pendingAnalysisSince = System.currentTimeMillis()) }
    }

    suspend fun check(id: String): Status {
        val interaction = try {
            gemini.get(id)
        } catch (e: GeminiException) {
            if (e.status == 404) {
                clearPending()
                return Status.Failed("The analysis is no longer on Google's servers. Run it again.")
            }
            throw e
        }
        return when (val status = interaction.string("status")) {
            "completed", "incomplete" -> finish(id, interaction, complete = status == "completed")
            "failed", "cancelled", "requires_action" -> {
                clearPending()
                runCatching { gemini.delete(id) }
                val reason = (interaction["error"] as? JsonObject)?.string("message")
                Status.Failed(reason ?: "Antigravity stopped before finishing. Try again.")
            }
            else -> Status.Running
        }
    }

    suspend fun cancel(id: String) {
        clearPending()
        runCatching { gemini.cancel(id) }
    }

    private suspend fun finish(id: String, interaction: JsonObject, complete: Boolean): Status {
        clearPending()
        val text = interaction.steps.finalOutputText().ifBlank { interaction.steps.outputText() }
        runCatching { gemini.delete(id) }
        if (text.isBlank()) return Status.Failed("Antigravity finished without writing a report. Try again.")
        val note = if (complete) "" else "\n\n*Antigravity reached its token limit, so this report may be incomplete.*"
        ledger.addReport(Report(createdAt = System.currentTimeMillis(), text = text + note))
        return Status.Done
    }

    private suspend fun clearPending() {
        settings.update { it.copy(pendingAnalysis = null, pendingAnalysisSince = null) }
    }

    private fun prompt(currency: String, monthlyBudget: Long?, today: LocalDate): String {
        val symbol = Money.of(currency).symbol
        val budget = monthlyBudget?.let { "The overall monthly budget is ${Amounts.plain(it)} $currency." }
            ?: "There's no overall monthly budget."
        return """
            You're a personal finance analyst. Today is $today. The user's budget data is in /workspace:
            - transactions.csv: every transaction (date, type, amount, category, note). Amounts are in $currency.
            - categories.csv: the categories, with their monthly budgets where set.
            $budget

            Analyze the data with code, then write a report for the user covering:
            1. This month so far: spending against the budgets and against the same point last month, and where the month is heading.
            2. Where the money goes: the biggest categories and how they've changed over the last few months.
            3. Recurring payments and subscriptions you can spot.
            4. Unusual or one-off spending worth a second look.
            5. Three to five specific, realistic ways to spend less, each with an estimated monthly saving.

            Write it in plain, friendly language, under 600 words. Use Markdown with ## headings, short paragraphs and bullet lists, and no tables, code or charts. Write amounts with the $symbol symbol. If there's too little data for a section, say so in one line.
            Put the whole report in your final message. Don't only save it to a file.
        """.trimIndent()
    }

    private companion object {
        const val TOKEN_BUDGET = 400_000
    }
}
