package io.github.geanyl17.opentracker.ai

import io.github.geanyl17.opentracker.data.Ledger
import io.github.geanyl17.opentracker.data.Report
import io.github.geanyl17.opentracker.data.SettingsStore

/** Has the AI on the user's PC, through the relay, study a year of transactions and write a report. The app polls until it's done. */
class Analyst(
    private val relay: RelayClient,
    private val ledger: Ledger,
    private val settings: SettingsStore,
) {
    sealed interface Status {
        data object Running : Status
        data object Done : Status
        data class Failed(val message: String) : Status
    }

    suspend fun start() {
        val context = FinanceContext.load(ledger, settings)
        if (context.isEmpty) throw RelayException("Add some transactions first.")
        val id = relay.startJob(prompt(context), context.settings.analysisModel)
        settings.update { it.copy(pendingAnalysis = id, pendingAnalysisSince = System.currentTimeMillis()) }
    }

    suspend fun check(id: String): Status {
        val job = try {
            relay.job(id)
        } catch (e: RelayException) {
            if (e.status != 404) throw e
            clearPending()
            return Status.Failed("Your PC no longer has this analysis, maybe because the relay restarted. Run it again.")
        }
        return when (job) {
            Job.Running -> Status.Running
            is Job.Failed -> {
                clearPending()
                Status.Failed(job.message)
            }
            is Job.Done -> {
                clearPending()
                if (job.text.isBlank()) return Status.Failed("The analysis finished without a report. Try again.")
                ledger.addReport(Report(createdAt = System.currentTimeMillis(), text = job.text))
                Status.Done
            }
        }
    }

    suspend fun cancel(id: String) {
        clearPending()
        runCatching { relay.cancelJob(id) }
    }

    private suspend fun clearPending() {
        settings.update { it.copy(pendingAnalysis = null, pendingAnalysisSince = null) }
    }

    private fun prompt(context: FinanceContext): String = """
        You're a personal finance analyst. Below is the user's budget data: an overview with categories, budgets and monthly totals, then every transaction from the past year as CSV (date, type, amount, category, note).

        Write a report for the user covering:
        1. This month so far: spending against the budgets and against the same point last month, and where the month is heading.
        2. Where the money goes: the biggest categories and how they've changed over the last few months.
        3. Recurring payments and subscriptions you can spot.
        4. Unusual or one-off spending worth a second look.
        5. Three to five specific, realistic ways to spend less, each with an estimated monthly saving.

        Write it in plain, friendly language, under 600 words. Use Markdown with ## headings, short paragraphs and bullet lists, and no tables or code. Write amounts with the ${context.symbol} symbol. Rely on the monthly totals where you can, and double-check any sums you work out yourself. If there's too little data for a section, say so in one line. Reply with the report only.

    """.trimIndent() + "\n\n" + context.overview() + "\nTransactions:\n" + context.transactionsCsv()
}
