package com.zynergy.forager.persistence

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.zynergy.forager.domain.Identification
import com.zynergy.forager.domain.IdentificationChange
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.Species
import com.zynergy.forager.domain.TaxonRank
import com.zynergy.forager.domain.TaxonSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

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
                  ('old-entry', 1758369600000, '47347', 'Cantharellus cibarius',
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
            notes = "no accuracy recorded",
            latitude = 47.5,
            longitude = -122.5,
            locationAccuracyMetres = null,
            photoCount = 0,
        )
        val entry = row.toEntry(
            listOf(IdentificationChange(Identification.Unidentified, Instant.ofEpochMilli(1758369600000))),
        )

        assertTrue("it has coordinates, so it can be drawn", entry.isMappable)
        assertTrue(
            "but with no measured accuracy it must not read as a confident point",
            !entry.hasPreciseLocation,
        )
        assertNull(
            "and the accuracy stays unknown, not replaced with a stand-in number",
            entry.where?.accuracyMetres,
        )
    }

    /**
     * Version 2 to 3 moves each entry's species into the identification history and drops the four
     * species columns. Four version 2 rows cover every shape the version 2 reader distinguished: a
     * full species, a genus with no common name, no species at all, and a half-written species
     * (catalog id, no name), which version 2 showed as unidentified.
     */
    @Test
    fun migratingToVersion3MovesEverySpeciesIntoTheHistoryWithoutLoss() = runTest {
        helper.createDatabase(TEST_DB, 2).use { v2 ->
            v2.execSQL(
                """
                INSERT INTO journal_entry
                  (id, recorded_at_epoch_millis, species_catalog_id, species_scientific_name,
                   species_common_name, species_rank, notes, latitude, longitude,
                   location_accuracy_metres, photo_count)
                VALUES
                  ('chanterelle', 1758369600000, '47347', 'Cantharellus cibarius',
                   'Golden Chanterelle', 'SPECIES', 'under douglas fir', 47.5, -122.5, 6.0, 2),
                  ('genus', 1758373200000, '47578', 'Russula', NULL, 'GENUS', '', NULL, NULL, NULL, 0),
                  ('unnamed', 1758376800000, NULL, NULL, NULL, NULL, 'orange bracket', NULL, NULL, NULL, 0),
                  ('half', 1758380400000, '99999', NULL, NULL, NULL, 'half written', NULL, NULL, NULL, 0)
                """.trimIndent(),
            )
        }

        val v3 = helper.runMigrationsAndValidate(TEST_DB, 3, true, ForagerDatabase.MIGRATION_2_3)

        v3.query("SELECT COUNT(*) FROM journal_entry").use {
            it.moveToFirst()
            assertEquals("every version 2 entry must still be there", 4, it.getInt(0))
        }
        v3.query(
            "SELECT entry_id, kind, source, catalog_id, scientific_name, common_name, rank, typed_name, " +
                "changed_at_epoch_millis FROM identification_change ORDER BY entry_id",
        ).use { c ->
            val rows = buildList {
                while (c.moveToNext()) {
                    add((0..8).map { i -> if (c.isNull(i)) null else c.getString(i) })
                }
            }
            assertEquals(
                "exactly one history row per entry, with the version 2 values copied as they were",
                listOf(
                    listOf("chanterelle", "TAXON", "NOT_RECORDED", "47347", "Cantharellus cibarius",
                        "Golden Chanterelle", "SPECIES", null, "1758369600000"),
                    listOf("genus", "TAXON", "NOT_RECORDED", "47578", "Russula", null, "GENUS", null,
                        "1758373200000"),
                    listOf("half", "UNIDENTIFIED", null, "99999", null, null, null, null, "1758380400000"),
                    listOf("unnamed", "UNIDENTIFIED", null, null, null, null, null, null, "1758376800000"),
                ),
                rows,
            )
        }
        v3.close()

        // And through the app's own store, on the migrated file, the way the app will read it.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, ForagerDatabase::class.java, TEST_DB)
            .addMigrations(*ForagerDatabase.MIGRATIONS)
            .build()
        val read = RoomJournalStore(db.journalDao()).all()
        db.close()

        val entries = (read as Outcome.Ok).value.associateBy { it.id }
        assertEquals(
            Identification.Taxon(
                Species("47347", "Cantharellus cibarius", "Golden Chanterelle", TaxonRank.SPECIES),
                TaxonSource.NOT_RECORDED,
            ),
            entries.getValue("chanterelle").identification,
        )
        assertEquals(Instant.ofEpochMilli(1758369600000), entries.getValue("chanterelle").identifications.single().at)
        assertEquals("under douglas fir", entries.getValue("chanterelle").notes)
        assertEquals(6.0, entries.getValue("chanterelle").where!!.accuracyMetres!!, 1e-9)
        assertEquals(2, entries.getValue("chanterelle").photoCount)
        assertEquals(
            Identification.Taxon(Species("47578", "Russula", null, TaxonRank.GENUS), TaxonSource.NOT_RECORDED),
            entries.getValue("genus").identification,
        )
        assertEquals(Identification.Unidentified, entries.getValue("unnamed").identification)
        assertEquals(Identification.Unidentified, entries.getValue("half").identification)
    }
}
