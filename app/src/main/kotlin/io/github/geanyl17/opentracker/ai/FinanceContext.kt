package io.github.geanyl17.opentracker.ai

import io.github.geanyl17.opentracker.data.Amounts
import io.github.geanyl17.opentracker.data.Category
import io.github.geanyl17.opentracker.data.Entry
import io.github.geanyl17.opentracker.data.Kind
import io.github.geanyl17.opentracker.data.Ledger
import io.github.geanyl17.opentracker.data.Money
import io.github.geanyl17.opentracker.data.Settings
import io.github.geanyl17.opentracker.data.SettingsStore
import io.github.geanyl17.opentracker.data.summarize
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** What the assistant knows about the user's money on each turn, rebuilt from the database every time. */
class FinanceContext(
    val today: LocalDate,
    val settings: Settings,
    val categories: List<Category>,
    private val entries: List<Entry>,
) {
    val currency: String = settings.currency
    private val symbol = Money.of(currency).symbol

    fun category(id: Long?): Category? = categories.firstOrNull { it.id == id }

    fun systemInstruction(): String = buildString {
        appendLine(
            """
            You are the assistant inside OpenTracker, the user's personal budget tracker app. The user often talks to you by voice and hears your replies read aloud.

            How to reply:
            - Answer in one to three short sentences unless the user asks for detail or advice. Reply in the language the user speaks.
            - Plain text only: no markdown, lists, tables or emoji.
            - Say amounts with the currency symbol and round sensibly, for example ${symbol}1,250 rather than 1250.00.
            - Use only the data below. Never invent transactions; if something isn't in the data, say so.
            - For insights, compare with budgets and earlier months, name the biggest categories and anything unusual, and give specific, practical suggestions. Don't lecture.

            Recording money:
            - When the user says they spent or received money, record it with add_transaction straight away, without asking first. The app shows an Undo button.
            - Pick the closest category. Use a short note describing what it was. Ask only if the amount is missing.
            - Several purchases in one message means one add_transaction call each.
            - "That" or "the last one" means the most recent transaction you recorded in this conversation, otherwise the one with the highest id.
            - Delete only when the user clearly asks for it. After recording, confirm in a few words and, if the category has a budget, mention what's left of it.
            """.trimIndent(),
        )
        appendLine()
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
        appendLine()
        val detailFrom = thisMonth.minusMonths(DETAILED_MONTHS - 1).atDay(1)
        val recent = entries.filter { !it.date.isBefore(detailFrom) }.take(MAX_DETAILED)
        appendLine("Transactions since $detailFrom, newest first (id | date | type | amount | category | note):")
        if (recent.isEmpty()) appendLine("(none)")
        recent.forEach { e ->
            appendLine("${e.id} | ${e.date} | ${e.kind.label} | ${Amounts.plain(e.amount)} | ${category(e.categoryId)?.name ?: "Uncategorized"} | ${e.note.replace('\n', ' ')}")
        }
    }

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
