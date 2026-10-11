package io.github.geanyl17.opentracker.data

import java.time.LocalDate
import java.time.YearMonth

private const val PACE_DAYS = 7

data class CategoryUsage(val categoryId: Long?, val name: String, val spent: Long, val budget: Long?)

data class MonthSummary(
    val month: YearMonth,
    val spent: Long,
    val income: Long,
    /** The monthly budget, or the sum of the category budgets when there's none. */
    val budget: Long?,
    val categories: List<CategoryUsage>,
    /** Days left including today, or null for a month that isn't the current one. */
    val daysLeft: Int?,
    val projected: Long?,
    val dailyAverage: Long,
) {
    val net: Long get() = income - spent
    val left: Long? get() = budget?.minus(spent)
    val perDayLeft: Long?
        get() {
            val left = left ?: return null
            val days = daysLeft ?: return null
            return if (left > 0 && days > 0) left / days else null
        }
}

fun summarize(
    month: YearMonth,
    entries: List<Entry>,
    categories: List<Category>,
    monthlyBudget: Long?,
    today: LocalDate,
): MonthSummary {
    val inMonth = entries.filter { YearMonth.from(it.date) == month }
    val expenses = inMonth.filter { it.kind == Kind.EXPENSE }
    val spent = expenses.sumOf { it.amount }
    val income = inMonth.filter { it.kind == Kind.INCOME }.sumOf { it.amount }

    val byId = categories.associateBy { it.id }
    val spentBy = expenses.groupBy { it.categoryId?.takeIf(byId::containsKey) }.mapValues { (_, list) -> list.sumOf { it.amount } }
    val budgeted = categories.filter { it.kind == Kind.EXPENSE && it.budget != null }.map { it.id }
    val usage = (spentBy.keys + budgeted).distinct().map { id ->
        val category = id?.let(byId::get)
        CategoryUsage(
            categoryId = id,
            name = category?.name ?: "Uncategorized",
            spent = spentBy[id] ?: 0,
            budget = category?.budget?.takeIf { category.kind == Kind.EXPENSE },
        )
    }.sortedWith(compareByDescending<CategoryUsage> { it.spent }.thenBy { it.name.lowercase() })

    val isCurrent = month == YearMonth.from(today)
    val daysElapsed = if (isCurrent) today.dayOfMonth else month.lengthOfMonth()
    // The last week's pace rather than the month's, so rent paid on the 1st doesn't count as daily spending.
    val projected = if (isCurrent && today.dayOfMonth > PACE_DAYS) {
        val lastWeek = expenses.filter { !it.date.isBefore(today.minusDays(PACE_DAYS - 1L)) && !it.date.isAfter(today) }.sumOf { it.amount }
        spent + lastWeek * (month.lengthOfMonth() - today.dayOfMonth) / PACE_DAYS
    } else {
        null
    }
    return MonthSummary(
        month = month,
        spent = spent,
        income = income,
        budget = monthlyBudget ?: categories.filter { it.kind == Kind.EXPENSE }.mapNotNull { it.budget }.sum().takeIf { it > 0 },
        categories = usage,
        daysLeft = if (isCurrent) month.lengthOfMonth() - today.dayOfMonth + 1 else null,
        projected = projected,
        dailyAverage = if (month.isAfter(YearMonth.from(today))) 0 else spent / daysElapsed,
    )
}
