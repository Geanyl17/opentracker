package io.github.geanyl17.opentracker.data

import java.time.LocalDate
import java.time.format.DateTimeParseException

object Csv {
    fun write(rows: List<List<String>>): String = buildString {
        rows.forEach { row -> append(row.joinToString(",", transform = ::escape)).append('\n') }
    }

    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        fun endField() {
            row += field.toString()
            field.clear()
        }
        fun endRow() {
            endField()
            if (row.size > 1 || row[0].isNotEmpty()) rows += row.toList()
            row.clear()
        }
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> field.append('"').also { i++ }
                quoted && c == '"' -> quoted = false
                quoted -> field.append(c)
                c == '"' -> quoted = true
                c == ',' -> endField()
                c == '\r' && text.getOrNull(i + 1) == '\n' -> Unit
                c == '\n' || c == '\r' -> endRow()
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    private fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}

/** The file format for exports, imports and the Antigravity sandbox: date,type,amount,category,note. */
object TransactionsCsv {
    private val header = listOf("date", "type", "amount", "category", "note")
    private val byteOrderMark = Char(0xFEFF)

    data class Row(val date: LocalDate, val kind: Kind, val amount: Long, val category: String?, val note: String)

    fun export(entries: List<Entry>, categories: List<Category>): String {
        val names = categories.associate { it.id to it.name }
        return Csv.write(
            listOf(header) + entries.sortedWith(compareBy({ it.date }, { it.createdAt })).map {
                listOf(
                    it.date.toString(),
                    it.kind.name.lowercase(),
                    Amounts.plain(it.amount),
                    it.categoryId?.let(names::get).orEmpty(),
                    it.note,
                )
            },
        )
    }

    /** Columns are found by name, so their order doesn't matter. Throws [IllegalArgumentException] on bad rows. */
    fun read(text: String): List<Row> {
        val rows = Csv.parse(text.trimStart(byteOrderMark))
        if (rows.isEmpty()) return emptyList()
        val columns = rows.first().map { it.trim().lowercase() }
        fun index(name: String) = columns.indexOf(name).takeIf { it >= 0 }
        val date = index("date") ?: throw IllegalArgumentException("The file has no \"date\" column.")
        val amount = index("amount") ?: throw IllegalArgumentException("The file has no \"amount\" column.")
        val type = index("type")
        val category = index("category")
        val note = index("note")

        return rows.drop(1).mapIndexed { i, cells ->
            val line = i + 2
            fun cell(index: Int?) = index?.let(cells::getOrNull)?.trim().orEmpty()
            Row(
                date = try {
                    LocalDate.parse(cell(date))
                } catch (e: DateTimeParseException) {
                    throw IllegalArgumentException("Line $line: \"${cell(date)}\" isn't a date like 2026-01-31.")
                },
                kind = if (cell(type).equals("income", ignoreCase = true)) Kind.INCOME else Kind.EXPENSE,
                amount = Amounts.parse(cell(amount))?.takeIf { it > 0 }
                    ?: throw IllegalArgumentException("Line $line: \"${cell(amount)}\" isn't an amount."),
                category = cell(category).ifEmpty { null },
                note = cell(note),
            )
        }
    }

    fun key(date: LocalDate, kind: Kind, amount: Long, category: String?, note: String) =
        listOf(date, kind, amount, category.orEmpty(), note.trim())
}
