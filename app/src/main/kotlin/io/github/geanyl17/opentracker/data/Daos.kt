package io.github.geanyl17.opentracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE date BETWEEN :from AND :to ORDER BY date DESC, createdAt DESC")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE date >= :from ORDER BY date DESC, createdAt DESC")
    suspend fun since(from: LocalDate): List<Entry>

    @Query("SELECT * FROM entries ORDER BY date, createdAt")
    suspend fun all(): List<Entry>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun get(id: Long): Entry?

    @Insert
    suspend fun insert(entry: Entry): Long

    @Insert
    suspend fun insertAll(entries: List<Entry>)

    @Update
    suspend fun update(entry: Entry)

    @Delete
    suspend fun delete(entry: Entry)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY kind, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY kind, name COLLATE NOCASE")
    suspend fun all(): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: Long): Category?

    @Insert
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)
}

@Dao
interface ReportDao {
    @Query("SELECT * FROM reports ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Report>>

    @Insert
    suspend fun insert(report: Report): Long

    @Delete
    suspend fun delete(report: Report)
}
