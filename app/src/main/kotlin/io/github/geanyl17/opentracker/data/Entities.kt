package io.github.geanyl17.opentracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.time.LocalDate

enum class Kind { EXPENSE, INCOME }

@Entity(tableName = "categories", indices = [Index(value = ["name", "kind"], unique = true)])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: Kind,
    val budget: Long? = null,
)

@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("categoryId"), Index("date")],
)
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: Kind,
    /** In hundredths of the currency unit, always positive: [kind] gives the direction. */
    val amount: Long,
    val categoryId: Long?,
    val note: String,
    val date: LocalDate,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "reports")
data class Report(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val text: String,
)

class Converters {
    @TypeConverter
    fun dateToEpochDay(date: LocalDate): Long = date.toEpochDay()

    @TypeConverter
    fun epochDayToDate(day: Long): LocalDate = LocalDate.ofEpochDay(day)
}
