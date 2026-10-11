package io.github.geanyl17.opentracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Category::class, Entry::class, Report::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entries(): EntryDao
    abstract fun categories(): CategoryDao
    abstract fun reports(): ReportDao

    companion object {
        private val defaultCategories = listOf(
            "Food", "Groceries", "Transport", "Bills", "Shopping", "Health", "Fun", "Other",
        ).map { it to Kind.EXPENSE } + listOf(
            "Salary", "Freelance", "Gifts", "Other income",
        ).map { it to Kind.INCOME }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "opentracker.db")
                .addCallback(
                    object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            defaultCategories.forEach { (name, kind) ->
                                db.execSQL("INSERT INTO categories (name, kind) VALUES (?, ?)", arrayOf(name, kind.name))
                            }
                        }
                    },
                )
                .build()
    }
}
