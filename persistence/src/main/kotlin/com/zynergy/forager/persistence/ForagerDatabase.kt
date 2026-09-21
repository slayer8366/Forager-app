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
 * code that reads it. Version 3 moves an entry's species into a history of identifications. Migrations are declared explicitly and never fall back to destructive
 * recreation: losing a forager's records to a schema change would be the single worst thing this
 * app could do, and `fallbackToDestructiveMigration` does exactly that, quietly.
 */
@Database(
    entities = [
        JournalEntryRow::class,
        IdentificationChangeRow::class,
        TripPlanRow::class,
        TripPlanTargetRow::class,
    ],
    version = 3,
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

        /**
         * Moves each entry's species into the identification history, then drops the four species
         * columns from journal_entry.
         *
         * Every entry gets exactly one history row, dated when the entry was recorded. That date is
         * true, not assumed: version 2 had no way to change a species after saving. Its source is
         * NOT_RECORDED, because version 2 never recorded how a species was chosen.
         *
         * The kind is TAXON exactly when the version 2 reader built a species, which was when both
         * the catalog id and the scientific name were present, and UNIDENTIFIED otherwise, which is
         * how version 2 showed every other row. The four species values are copied across as they
         * are in every case, so nothing on disk is dropped, including a half-written species that
         * neither version can read as one.
         *
         * SQLite on this app's oldest supported Android cannot drop a column, so journal_entry is
         * rebuilt: create the new shape, copy, drop the old table, rename.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `identification_change` (" +
                        "`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `entry_id` TEXT NOT NULL, " +
                        "`changed_at_epoch_millis` INTEGER NOT NULL, `kind` TEXT NOT NULL, `source` TEXT, " +
                        "`catalog_id` TEXT, `scientific_name` TEXT, `common_name` TEXT, `rank` TEXT, " +
                        "`typed_name` TEXT)",
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_identification_change_entry_id` " +
                        "ON `identification_change` (`entry_id`)",
                )
                connection.execSQL(
                    """
                    INSERT INTO identification_change
                      (entry_id, changed_at_epoch_millis, kind, source,
                       catalog_id, scientific_name, common_name, rank, typed_name)
                    SELECT id, recorded_at_epoch_millis,
                      CASE WHEN species_catalog_id IS NOT NULL AND species_scientific_name IS NOT NULL
                           THEN 'TAXON' ELSE 'UNIDENTIFIED' END,
                      CASE WHEN species_catalog_id IS NOT NULL AND species_scientific_name IS NOT NULL
                           THEN 'NOT_RECORDED' ELSE NULL END,
                      species_catalog_id, species_scientific_name, species_common_name, species_rank,
                      NULL
                    FROM journal_entry
                    ORDER BY recorded_at_epoch_millis, id
                    """.trimIndent(),
                )
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `journal_entry_v3` (`id` TEXT NOT NULL, " +
                        "`recorded_at_epoch_millis` INTEGER NOT NULL, `notes` TEXT NOT NULL, " +
                        "`latitude` REAL, `longitude` REAL, `location_accuracy_metres` REAL, " +
                        "`photo_count` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                connection.execSQL(
                    "INSERT INTO journal_entry_v3 (id, recorded_at_epoch_millis, notes, latitude, longitude, " +
                        "location_accuracy_metres, photo_count) " +
                        "SELECT id, recorded_at_epoch_millis, notes, latitude, longitude, " +
                        "location_accuracy_metres, photo_count FROM journal_entry",
                )
                connection.execSQL("DROP TABLE journal_entry")
                connection.execSQL("ALTER TABLE journal_entry_v3 RENAME TO journal_entry")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        fun open(context: Context): ForagerDatabase =
            Room.databaseBuilder(context.applicationContext, ForagerDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
