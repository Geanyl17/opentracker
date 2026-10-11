package io.github.geanyl17.opentracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthSummaryTest {
    private val october = YearMonth.of(2026, 10)
    private val today = LocalDate.of(2026, 10, 11)
    private val food = Category(id = 1, name = "Food", kind = Kind.EXPENSE, budget = 600_000)
    private val transport = Category(id = 2, name = "Transport", kind = Kind.EXPENSE)
    private val bills = Category(id = 3, name = "Bills", kind = Kind.EXPENSE, budget = 400_000)
    private val salary = Category(id = 4, name = "Salary", kind = Kind.INCOME)

    private fun entry(kind: Kind, amount: Long, category: Category?, day: Int, month: YearMonth = october) =
        Entry(kind = kind, amount = amount, categoryId = category?.id, note = "", date = month.atDay(day))

    private val entries = listOf(
        entry(Kind.EXPENSE, 250_000, food, 2),
        entry(Kind.EXPENSE, 100_000, food, 9),
        entry(Kind.EXPENSE, 50_000, transport, 5),
        entry(Kind.EXPENSE, 20_000, null, 6),
        entry(Kind.INCOME, 3_000_000, salary, 1),
        entry(Kind.EXPENSE, 999_900, food, 20, october.minusMonths(1)),
    )

    @Test
    fun totalsTheMonth() {
        val summary = summarize(october, entries, listOf(food, transport, bills, salary), monthlyBudget = 1_000_000, today)
        assertEquals(420_000L, summary.spent)
        assertEquals(3_000_000L, summary.income)
        assertEquals(2_580_000L, summary.net)
        assertEquals(1_000_000L, summary.budget)
        assertEquals(21, summary.daysLeft)
        assertEquals(27_619L, summary.perDayLeft)
        assertEquals(420_000L + 170_000L * 20 / 7, summary.projected)
    }

    @Test
    fun listsSpendingAndBudgetedCategories() {
        val summary = summarize(october, entries, listOf(food, transport, bills, salary), monthlyBudget = null, today)
        assertEquals(
            listOf(
                CategoryUsage(1, "Food", 350_000, 600_000),
                CategoryUsage(2, "Transport", 50_000, null),
                CategoryUsage(null, "Uncategorized", 20_000, null),
                CategoryUsage(3, "Bills", 0, 400_000),
            ),
            summary.categories,
        )
        assertEquals(1_000_000L, summary.budget)
    }

    @Test
    fun pastMonthsHaveNoCountdown() {
        val summary = summarize(october.minusMonths(1), entries, listOf(food), monthlyBudget = null, today)
        assertEquals(999_900L, summary.spent)
        assertNull(summary.daysLeft)
        assertNull(summary.projected)
        assertEquals(33_330L, summary.dailyAverage)
    }
}
