package com.zynergy.forager.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * The app's database.
 *
 * `exportSchema` is on so each version's schema is committed and a later migration can be written
 * against a file rather than against memory.
 *
 * Version 2 adds the reported accuracy of a journal entry's location, in the same change as the
 * code that reads it. Migrations are declared explicitly and never fall back to destructive
 * recreation: losing a forager's records to a schema change would be the single worst thing this
 * app could do, and `fallbackToDestructiveMigration` does exactly that, quietly.
 */
@Database(
    entities = [JournalEntryRow::class, TripPlanRow::class, TripPlanTargetRow::class],
    version = 2,
    exportSchema = true,
)
abstract class ForagerDatabase : RoomDatabase() {

    abstract fun journalDao(): JournalDao
    abstract fun tripPlanDao(): TripPlanDao

    companion object {
        const val NAME = "forager.db"

        /** Adds the accuracy radius. Nullable, because version 1 rows never measured one. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE journal_entry ADD COLUMN location_accuracy_metres REAL",
                )
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2)

        fun open(context: Context): ForagerDatabase =
            Room.databaseBuilder(context.applicationContext, ForagerDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
