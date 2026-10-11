package io.github.geanyl17.opentracker.ai

import io.github.geanyl17.opentracker.data.Amounts
import io.github.geanyl17.opentracker.data.Category
import io.github.geanyl17.opentracker.data.Entry
import io.github.geanyl17.opentracker.data.Kind
import io.github.geanyl17.opentracker.data.Ledger
import io.github.geanyl17.opentracker.data.Money
import io.github.geanyl17.opentracker.data.Settings
import io.github.geanyl17.opentracker.data.SettingsStore
import io.github.geanyl17.opentracker.data.TransactionsCsv
import io.github.geanyl17.opentracker.data.summarize
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** What the model knows about the user's money, rebuilt from the database for every request. */
class FinanceContext(
    val today: LocalDate,
    val settings: Settings,
    val categories: List<Category>,
    private val entries: List<Entry>,
) {
    val currency: String = settings.currency
    val symbol: String = Money.of(currency).symbol

    fun category(id: Long?): Category? = categories.firstOrNull { it.id == id }

    fun assistantRules(): String = """
        You are the assistant inside OpenTracker, the user's personal budget tracker app. The user often talks to you by voice and hears your reply read aloud.

        Answer with JSON: "reply" is what you say to the user, and "actions" lists the changes the app should make, in order. The app makes the changes as it shows your reply, with an Undo button.

        How to reply:
        - One to three short sentences, unless the user asks for detail or advice. Reply in the language the user speaks.
        - Plain text only: no markdown, lists, tables or emoji.
        - Say amounts with the currency symbol and round sensibly, for example ${symbol}1,250 rather than 1250.00.
        - Use only the data below. Never invent transactions; if something isn't in the data, say so.
        - For insights, compare with budgets and earlier months, name the biggest categories and anything unusual, and give specific, practical suggestions. Don't lecture.

        Actions:
        - add_transaction: kind (expense or income), amount, category, note, and date (leave it out for today). One for each purchase or payment the user mentions, straight away and without asking first.
        - update_transaction: id, plus only the fields that change.
        - delete_transaction: id. Only when the user clearly asks for it.
        - set_budget: amount, and category for a category budget; leave category out for the overall monthly budget. 0 removes the budget.
        - Use the closest existing category. If the amount is missing, ask for it and leave actions empty.
        - "That" or "the last one" means the transaction you most recently recorded in this conversation, otherwise the one with the highest id.
        - After recording, say so in a few words and, if the category has a budget, what's left of it after this.
    """.trimIndent()

    /** Today's date, the currency, budgets, categories and a year of monthly totals. */
    fun overview(): String = buildString {
        appendLine("Today is ${today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}, $today. Currency: $currency ($symbol). All amounts are in $currency.")
        appendLine("Overall monthly budget: ${settings.monthlyBudget?.let(Amounts::plain) ?: "not set"}")
        appendLine()
        appendLine("Categories (name, type, monthly budget):")
        categories.forEach { c ->
            appendLine("- ${c.name}, ${c.kind.label}, ${c.budget?.let(Amounts::plain) ?: "no budget"}")
        }
        appendLine()
        appendLine("Monthly totals, newest first (month: expenses, income; expenses by category):")
        val thisMonth = YearMonth.from(today)
        (0L until MONTHS_OF_TOTALS).map { thisMonth.minusMonths(it) }.forEach { month ->
            val summary = summarize(month, entries, categories, settings.monthlyBudget, today)
            if (summary.spent == 0L && summary.income == 0L && month != thisMonth) return@forEach
            val byCategory = summary.categories.filter { it.spent > 0 }
                .joinToString("; ") { "${it.name} ${Amounts.plain(it.spent)}" }
            appendLine("${month.format(monthFormat)}: expenses ${Amounts.plain(summary.spent)}, income ${Amounts.plain(summary.income)}; $byCategory")
        }
    }

    /** The last few months of transactions with their ids, so the assistant can change them. */
    fun recentTransactions(): String = buildString {
        val from = YearMonth.from(today).minusMonths(DETAILED_MONTHS - 1).atDay(1)
        val recent = entries.filter { !it.date.isBefore(from) }.take(MAX_DETAILED)
        appendLine("Transactions since $from, newest first (id | date | type | amount | category | note):")
        if (recent.isEmpty()) appendLine("(none)")
        recent.forEach { e ->
            appendLine("${e.id} | ${e.date} | ${e.kind.label} | ${Amounts.plain(e.amount)} | ${category(e.categoryId)?.name ?: "Uncategorized"} | ${e.note.replace('\n', ' ')}")
        }
    }

    /** Every transaction of the past year, as CSV. */
    fun transactionsCsv(): String = TransactionsCsv.export(entries, categories)

    val isEmpty: Boolean get() = entries.isEmpty()

    companion object {
        private const val MONTHS_OF_TOTALS = 12L
        private const val DETAILED_MONTHS = 3L
        private const val MAX_DETAILED = 600
        private val monthFormat = DateTimeFormatter.ofPattern("yyyy-MM")

        suspend fun load(ledger: Ledger, settings: SettingsStore, today: LocalDate = LocalDate.now()) = FinanceContext(
            today = today,
            settings = settings.current(),
            categories = ledger.allCategories(),
            entries = ledger.entriesSince(YearMonth.from(today).minusMonths(MONTHS_OF_TOTALS - 1).atDay(1)),
        )
    }
}

val Kind.label: String get() = name.lowercase()
