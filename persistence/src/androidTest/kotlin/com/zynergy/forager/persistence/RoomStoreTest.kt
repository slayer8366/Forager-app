package com.zynergy.forager.persistence

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Coordinates
import com.zynergy.forager.domain.Fix
import com.zynergy.forager.domain.Identification
import com.zynergy.forager.domain.IdentificationChange
import com.zynergy.forager.domain.JournalEntry
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.Species
import com.zynergy.forager.domain.TaxonRank
import com.zynergy.forager.domain.TaxonSource
import com.zynergy.forager.domain.TripPlan
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

private val CHANTERELLE = Species("47347", "Cantharellus cibarius", "Golden Chanterelle", TaxonRank.SPECIES)
private val TURKEY_TAIL = Species("48435", "Trametes versicolor", "turkey-tail", TaxonRank.SPECIES)
private val PUGET = BoundingBox(south = 47.0, west = -123.0, north = 47.9, east = -122.1)
private val NOON = Instant.parse("2026-09-20T12:00:00Z")

class RoomStoreTest {

    private lateinit var db: ForagerDatabase
    private lateinit var journal: RoomJournalStore
    private lateinit var plans: RoomTripPlanStore

    @Before
    fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, ForagerDatabase::class.java).build()
        journal = RoomJournalStore(db.journalDao())
        plans = RoomTripPlanStore(db.tripPlanDao())
    }

    @After
    fun close() = db.close()

    private fun <T> value(outcome: Outcome<T>): T = when (outcome) {
        is Outcome.Ok -> outcome.value
        is Outcome.Partial -> outcome.value
        is Outcome.Failed -> throw AssertionError("expected data, got failure: ${outcome.reason}")
        is Outcome.Unsupported -> throw AssertionError("expected data, got unsupported")
    }

    @Test
    fun anEntryWithEverythingSurvivesTheRoundTrip() = runTest {
        val entry = JournalEntry.first(
            id = "e1",
            recordedAt = NOON,
            identification = Identification.Taxon(CHANTERELLE, TaxonSource.SEARCH),
            notes = "under douglas fir",
            where = Fix(Coordinates(47.5, -122.5), accuracyMetres = 8.0),
            photoCount = 3,
        )
        journal.save(entry)

        val read = value(journal.all()).single()

        assertEquals("e1", read.id)
        assertEquals(NOON, read.recordedAt)
        assertEquals(Identification.Taxon(CHANTERELLE, TaxonSource.SEARCH), read.identification)
        assertEquals(NOON, read.identifications.single().at)
        assertEquals("under douglas fir", read.notes)
        assertEquals(Fix(Coordinates(47.5, -122.5), 8.0), read.where)
        assertEquals(3, read.photoCount)
    }

    @Test
    fun anEntryWithNoSpeciesAndNoLocationComesBackThatWay() = runTest {
        journal.save(
            JournalEntry.first(
                id = "e2", recordedAt = NOON, identification = Identification.Unidentified,
                notes = "orange bracket", where = null,
            ),
        )

        val read = value(journal.all()).single()

        assertEquals(Identification.Unidentified, read.identification)
        assertNull(read.where)
        assertTrue(!read.isMappable)
    }

    @Test
    fun entriesComeBackMostRecentFirst() = runTest {
        journal.save(JournalEntry.first("old", NOON.minusSeconds(3600), Identification.Unidentified, "older", null))
        journal.save(JournalEntry.first("new", NOON, Identification.Unidentified, "newer", null))

        assertEquals(listOf("new", "old"), value(journal.all()).map { it.id })
    }

    @Test
    fun aTypedNameComesBackExactlyAsTypedAndUnconfirmed() = runTest {
        journal.save(JournalEntry.first("typed", NOON, Identification.Unconfirmed("salmon "), "", null))

        val read = value(journal.all()).single()

        assertEquals(Identification.Unconfirmed("salmon "), read.identification)
        assertNull("a typed name is never a species", read.species)
    }

    /**
     * The history is the point: a change is appended, and what the entry was identified as before
     * stays exactly as it was, in order, each with its own time.
     */
    @Test
    fun aChangedIdentificationKeepsEveryEarlierOne() = runTest {
        journal.save(JournalEntry.first("e1", NOON, Identification.Unidentified, "orange, under fir", null))
        val second = IdentificationChange(Identification.Unconfirmed("chanterelle?"), NOON.plusSeconds(60))
        val third = IdentificationChange(Identification.Taxon(CHANTERELLE, TaxonSource.SEARCH), NOON.plusSeconds(120))

        value(journal.addIdentification("e1", second))
        val returned = value(journal.addIdentification("e1", third))
        val read = value(journal.all()).single()

        val expected = listOf(IdentificationChange(Identification.Unidentified, NOON), second, third)
        assertEquals(expected, returned.identifications)
        assertEquals(expected, read.identifications)
    }

    @Test
    fun identifyingAnEntryThatDoesNotExistFailsAndWritesNothing() = runTest {
        val result = journal.addIdentification(
            "missing",
            IdentificationChange(Identification.Taxon(CHANTERELLE, TaxonSource.SEARCH), NOON),
        )

        assertTrue(result is Outcome.Failed)
        assertEquals(0, db.journalDao().allChanges().size)
    }

    @Test
    fun savingAnEntryWhoseIdExistsIsRefusedAndTheHistoryIsUntouched() = runTest {
        val first = JournalEntry.first("e1", NOON, Identification.Taxon(CHANTERELLE, TaxonSource.SEARCH), "", null)
        journal.save(first)

        val again = journal.save(JournalEntry.first("e1", NOON, Identification.Unidentified, "overwrite?", null))

        assertTrue(again is Outcome.Failed)
        assertEquals(first, value(journal.all()).single())
    }

    /** One damaged row is reported, not shown as an unidentified find and not allowed to hide the rest. */
    @Test
    fun anEntryWhoseIdentificationCannotBeReadIsReportedNotShownAsUnidentified() = runTest {
        journal.save(JournalEntry.first("good", NOON, Identification.Unidentified, "fine", null))
        db.journalDao().save(
            JournalEntryRow("bad", NOON.toEpochMilli(), "damaged", null, null, null, 0),
            listOf(IdentificationChangeRow(0, "bad", NOON.toEpochMilli(), "TAXON", "SEARCH", null, null, null, null, null)),
        )

        val read = journal.all()

        assertTrue("expected a partial answer, got $read", read is Outcome.Partial)
        read as Outcome.Partial
        assertEquals(listOf("good"), read.value.map { it.id })
        assertTrue(read.note.contains("1 journal entry could not be read"))
    }

    @Test
    fun aPlanKeepsItsTargetsAndTheirOrder() = runTest {
        val plan = TripPlan(
            id = "p1",
            name = "Autumn walk",
            date = LocalDate.of(2026, 10, 12),
            area = PUGET,
            targets = listOf(TURKEY_TAIL, CHANTERELLE),
        )
        plans.save(plan)

        val read = value(plans.upcoming(LocalDate.of(2026, 9, 1))).single()

        assertEquals("Autumn walk", read.name)
        assertEquals(PUGET, read.area)
        assertEquals(listOf(TURKEY_TAIL, CHANTERELLE), read.targets)
    }

    @Test
    fun resavingAPlanWithFewerTargetsActuallyRemovesTheOldOnes() = runTest {
        val plan = TripPlan("p1", "Walk", LocalDate.of(2026, 10, 12), PUGET, listOf(TURKEY_TAIL, CHANTERELLE))
        plans.save(plan)
        plans.save(plan.copy(targets = listOf(CHANTERELLE)))

        val read = value(plans.upcoming(LocalDate.of(2026, 9, 1))).single()

        assertEquals(
            listOf(CHANTERELLE),
            read.targets,
            )
    }

    /**
     * Editing in place rests on this: saving a plan whose id is already stored replaces it, name,
     * date, area and targets, and leaves one row, not two.
     */
    @Test
    fun savingAPlanWithAnExistingIdReplacesItInPlace() = runTest {
        val original = TripPlan("p1", "Walk", LocalDate.of(2026, 10, 12), PUGET, listOf(TURKEY_TAIL))
        plans.save(original)
        val edited = original.copy(
            name = "Walk, north end",
            date = LocalDate.of(2026, 10, 13),
            area = BoundingBox(south = 47.5, west = -122.6, north = 47.8, east = -122.2),
            targets = listOf(CHANTERELLE, TURKEY_TAIL),
        )
        plans.save(edited)

        assertEquals(listOf(edited), value(plans.upcoming(LocalDate.of(2026, 9, 1))))
    }

    @Test
    fun aPlanSavedUnderANewIdSitsBesideTheOriginal() = runTest {
        val original = TripPlan("p1", "Walk", LocalDate.of(2026, 10, 12), PUGET, listOf(TURKEY_TAIL))
        plans.save(original)
        plans.save(original.copy(id = "p2", name = "Walk (copy)"))

        val read = value(plans.upcoming(LocalDate.of(2026, 9, 1))).sortedBy { it.id }
        assertEquals(listOf(original, original.copy(id = "p2", name = "Walk (copy)")), read)
        assertNotEquals(read[0].id, read[1].id)
    }

    @Test
    fun plansBeforeTheGivenDateAreNotUpcoming() = runTest {
        plans.save(TripPlan("past", "Last month", LocalDate.of(2026, 8, 1), PUGET, emptyList()))
        plans.save(TripPlan("future", "Next month", LocalDate.of(2026, 10, 1), PUGET, emptyList()))

        assertEquals(listOf("future"), value(plans.upcoming(LocalDate.of(2026, 9, 20))).map { it.id })
    }

    @Test
    fun aPlanWithNoTargetsIsStillAPlan() = runTest {
        plans.save(TripPlan("p1", "Scouting", LocalDate.of(2026, 10, 12), PUGET, emptyList()))

        val read = value(plans.upcoming(LocalDate.of(2026, 9, 1))).single()
        assertTrue(read.targets.isEmpty())
    }
}

/**
 * The claim persistence actually makes is that data is still there after the app is gone. An
 * in-memory database cannot show that, so this one writes to a real file, closes it, and opens it
 * again.
 */
class DatabaseSurvivesReopeningTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "survives-test.db"

    @Before
    @After
    fun removeFile() {
        context.deleteDatabase(name)
    }

    @Test
    fun whatWasSavedIsStillThereAfterCloseAndReopen() = runTest {
        val first = Room.databaseBuilder(context, ForagerDatabase::class.java, name).build()
        RoomJournalStore(first.journalDao()).save(
            JournalEntry.first(
                "kept", NOON, Identification.Taxon(CHANTERELLE, TaxonSource.PLAN_TARGET), "in the moss",
                Fix(Coordinates(47.6, -122.4), 12.0),
            ),
        )
        first.close()

        val second = Room.databaseBuilder(context, ForagerDatabase::class.java, name).build()
        val read = RoomJournalStore(second.journalDao()).all()
        second.close()

        val entries = (read as Outcome.Ok).value
        assertEquals(1, entries.size)
        assertEquals("in the moss", entries.single().notes)
        assertEquals(Identification.Taxon(CHANTERELLE, TaxonSource.PLAN_TARGET), entries.single().identification)
        assertEquals(Fix(Coordinates(47.6, -122.4), 12.0), entries.single().where)
    }
}
