package io.github.geanyl17.opentracker.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

class Ledger(private val db: AppDatabase) {
    val entries: Flow<List<Entry>> = db.entries().observeAll()
    val categories: Flow<List<Category>> = db.categories().observeAll()
    val reports: Flow<List<Report>> = db.reports().observeAll()

    fun entriesIn(month: YearMonth): Flow<List<Entry>> =
        db.entries().observeBetween(month.atDay(1), month.atEndOfMonth())

    suspend fun entry(id: Long): Entry? = db.entries().get(id)

    suspend fun entriesSince(date: LocalDate): List<Entry> = db.entries().since(date)

    suspend fun allEntries(): List<Entry> = db.entries().all()

    suspend fun allCategories(): List<Category> = db.categories().all()

    suspend fun add(entry: Entry): Long = db.entries().insert(entry)

    suspend fun update(entry: Entry) = db.entries().update(entry)

    suspend fun delete(entry: Entry) = db.entries().delete(entry)

    /** Puts back a deleted entry with its old id, dropping its category if that was deleted since. */
    suspend fun restore(entry: Entry) {
        val categoryId = entry.categoryId?.takeIf { db.categories().get(it) != null }
        db.entries().insert(entry.copy(categoryId = categoryId))
    }

    suspend fun saveCategory(category: Category): Long =
        if (category.id == 0L) {
            db.categories().insert(category)
        } else {
            db.categories().update(category)
            category.id
        }

    suspend fun deleteCategory(category: Category) = db.categories().delete(category)

    suspend fun addReport(report: Report): Long = db.reports().insert(report)

    suspend fun deleteReport(report: Report) = db.reports().delete(report)

    /**
     * Adds the rows that aren't in the ledger yet, creating missing categories, and returns how many were added.
     * A row counts as present when an entry with the same date, type, amount, category and note exists, so
     * importing the same file twice changes nothing.
     */
    suspend fun import(rows: List<TransactionsCsv.Row>): Int = db.withTransaction {
        val categories = db.categories().all().toMutableList()
        suspend fun categoryFor(name: String?, kind: Kind): Long? {
            if (name.isNullOrBlank()) return null
            categories.firstOrNull { it.kind == kind && it.name.equals(name, ignoreCase = true) }?.let { return it.id }
            val created = Category(name = name.trim(), kind = kind)
            return created.copy(id = db.categories().insert(created)).also { categories += it }.id
        }

        val names = categories.associate { it.id to it.name.lowercase() }
        val existing = db.entries().all()
            .groupingBy { TransactionsCsv.key(it.date, it.kind, it.amount, it.categoryId?.let(names::get), it.note) }
            .eachCount()
            .toMutableMap()

        val added = rows.mapNotNull { row ->
            val key = TransactionsCsv.key(row.date, row.kind, row.amount, row.category?.lowercase(), row.note)
            val seen = existing[key] ?: 0
            if (seen > 0) {
                existing[key] = seen - 1
                null
            } else {
                Entry(
                    kind = row.kind,
                    amount = row.amount,
                    categoryId = categoryFor(row.category, row.kind),
                    note = row.note,
                    date = row.date,
                )
            }
        }
        db.entries().insertAll(added)
        added.size
    }
}
