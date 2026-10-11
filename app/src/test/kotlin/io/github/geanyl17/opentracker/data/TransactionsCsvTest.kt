package io.github.geanyl17.opentracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class TransactionsCsvTest {
    private val food = Category(id = 1, name = "Food", kind = Kind.EXPENSE)
    private val salary = Category(id = 2, name = "Salary", kind = Kind.INCOME)

    @Test
    fun exportsAndReadsBack() {
        val entries = listOf(
            Entry(id = 1, kind = Kind.EXPENSE, amount = 25_050, categoryId = 1, note = "Lunch, with \"Ana\"", date = LocalDate.of(2026, 10, 2)),
            Entry(id = 2, kind = Kind.INCOME, amount = 3_000_000, categoryId = 2, note = "Line one\nline two", date = LocalDate.of(2026, 10, 1)),
            Entry(id = 3, kind = Kind.EXPENSE, amount = 1_000, categoryId = null, note = "", date = LocalDate.of(2026, 10, 3)),
        )
        val csv = TransactionsCsv.export(entries, listOf(food, salary))

        assertEquals(
            listOf(
                TransactionsCsv.Row(LocalDate.of(2026, 10, 1), Kind.INCOME, 3_000_000, "Salary", "Line one\nline two"),
                TransactionsCsv.Row(LocalDate.of(2026, 10, 2), Kind.EXPENSE, 25_050, "Food", "Lunch, with \"Ana\""),
                TransactionsCsv.Row(LocalDate.of(2026, 10, 3), Kind.EXPENSE, 1_000, null, ""),
            ),
            TransactionsCsv.read(csv),
        )
    }

    @Test
    fun findsColumnsByName() {
        val csv = "Note,Amount,Date\r\nCoffee,\"1,250.00\",2026-09-30\r\n"
        assertEquals(
            listOf(TransactionsCsv.Row(LocalDate.of(2026, 9, 30), Kind.EXPENSE, 125_000, null, "Coffee")),
            TransactionsCsv.read(csv),
        )
    }

    @Test
    fun reportsTheBadLine() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            TransactionsCsv.read("date,amount\n2026-01-01,5\n01/02/2026,5\n")
        }
        assertEquals("Line 3: \"01/02/2026\" isn't a date like 2026-01-31.", error.message)
    }
}
