package com.zynergy.forager.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's database.
 *
 * `exportSchema` is on so each version's schema is committed and a later migration can be written
 * against a file rather than against memory. Version 1 is the first shipped schema; the accuracy
 * column the location work will need becomes version 2, with its reader, in that change.
 */
@Database(
    entities = [JournalEntryRow::class, TripPlanRow::class, TripPlanTargetRow::class],
    version = 1,
    exportSchema = true,
)
abstract class ForagerDatabase : RoomDatabase() {

    abstract fun journalDao(): JournalDao
    abstract fun tripPlanDao(): TripPlanDao

    companion object {
        const val NAME = "forager.db"

        fun open(context: Context): ForagerDatabase =
            Room.databaseBuilder(context.applicationContext, ForagerDatabase::class.java, NAME)
                .build()
    }
}
