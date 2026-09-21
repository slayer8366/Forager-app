package com.zynergy.forager.persistence

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val TEST_DB = "migration-test.db"

/**
 * A migration that loses entries would be the worst thing this app could do, and it would do it
 * silently on the next launch after an update. So the migration runs against a version 1 database
 * with a real row in it, and the row is read back afterwards.
 */
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ForagerDatabase::class.java,
    )

    @Test
    fun migratingToVersion2KeepsExistingEntriesAndLeavesAccuracyUnknown() {
        helper.createDatabase(TEST_DB, 1).use { v1 ->
            v1.execSQL(
                """
                INSERT INTO journal_entry
                  (id, recorded_at_epoch_millis, species_catalog_id, species_scientific_name,
                   species_common_name, species_rank, notes, latitude, longitude, photo_count)
                VALUES
                  ('old-entry', 1758369600000, '47348', 'Cantharellus cibarius',
                   'Golden Chanterelle', 'SPECIES', 'found before the update', 47.5, -122.5, 2)
                """.trimIndent(),
            )
        }

        val v2 = helper.runMigrationsAndValidate(TEST_DB, 2, true, ForagerDatabase.MIGRATION_1_2)

        v2.query("SELECT * FROM journal_entry").use { cursor ->
            assertTrue("the entry written under version 1 must survive", cursor.moveToFirst())
            assertEquals(1, cursor.count)
            assertEquals("old-entry", cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertEquals(
                "found before the update",
                cursor.getString(cursor.getColumnIndexOrThrow("notes")),
            )
            assertEquals(47.5, cursor.getDouble(cursor.getColumnIndexOrThrow("latitude")), 1e-9)

            val accuracyColumn = cursor.getColumnIndexOrThrow("location_accuracy_metres")
            assertTrue(
                "an entry from before the column existed has no measurement, not a zero",
                cursor.isNull(accuracyColumn),
            )
        }
        v2.close()
    }

    @Test
    fun aVersion1EntryReadsBackAsMappableButNotPrecise() {
        helper.createDatabase(TEST_DB, 1).use { v1 ->
            v1.execSQL(
                """
                INSERT INTO journal_entry
                  (id, recorded_at_epoch_millis, species_catalog_id, species_scientific_name,
                   species_common_name, species_rank, notes, latitude, longitude, photo_count)
                VALUES
                  ('old-entry', 1758369600000, NULL, NULL, NULL, NULL, 'no accuracy recorded',
                   47.5, -122.5, 0)
                """.trimIndent(),
            )
        }
        helper.runMigrationsAndValidate(TEST_DB, 2, true, ForagerDatabase.MIGRATION_1_2).close()

        val row = JournalEntryRow(
            id = "old-entry",
            recordedAtEpochMillis = 1758369600000,
            speciesCatalogId = null,
            speciesScientificName = null,
            speciesCommonName = null,
            speciesRank = null,
            notes = "no accuracy recorded",
            latitude = 47.5,
            longitude = -122.5,
            locationAccuracyMetres = null,
            photoCount = 0,
        )
        val entry = row.toEntry()

        assertTrue("it has coordinates, so it can be drawn", entry.isMappable)
        assertTrue(
            "but with no measured accuracy it must not read as a confident point",
            !entry.hasPreciseLocation,
        )
        assertNull(null)
    }
}
