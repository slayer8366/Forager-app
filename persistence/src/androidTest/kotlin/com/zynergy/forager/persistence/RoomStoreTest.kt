package com.zynergy.forager.persistence

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Coordinates
import com.zynergy.forager.domain.Fix
import com.zynergy.forager.domain.JournalEntry
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.Species
import com.zynergy.forager.domain.TaxonRank
import com.zynergy.forager.domain.TripPlan
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

private val CHANTERELLE = Species("47348", "Cantharellus cibarius", "Golden Chanterelle", TaxonRank.SPECIES)
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
        val entry = JournalEntry(
            id = "e1",
            recordedAt = NOON,
            species = CHANTERELLE,
            notes = "under douglas fir",
            where = Fix(Coordinates(47.5, -122.5), accuracyMetres = 8.0),
            photoCount = 3,
        )
        journal.save(entry)

        val read = value(journal.all()).single()

        assertEquals("e1", read.id)
        assertEquals(NOON, read.recordedAt)
        assertEquals(CHANTERELLE, read.species)
        assertEquals("under douglas fir", read.notes)
        assertEquals(Fix(Coordinates(47.5, -122.5), 8.0), read.where)
        assertEquals(3, read.photoCount)
    }

    @Test
    fun anEntryWithNoSpeciesAndNoLocationComesBackThatWay() = runTest {
        journal.save(
            JournalEntry(id = "e2", recordedAt = NOON, species = null, notes = "orange bracket", where = null),
        )

        val read = value(journal.all()).single()

        assertNull(read.species)
        assertNull(read.where)
        assertTrue(!read.isMappable)
    }

    @Test
    fun entriesComeBackMostRecentFirst() = runTest {
        journal.save(JournalEntry("old", NOON.minusSeconds(3600), null, "older", null))
        journal.save(JournalEntry("new", NOON, null, "newer", null))

        assertEquals(listOf("new", "old"), value(journal.all()).map { it.id })
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
            JournalEntry("kept", NOON, CHANTERELLE, "in the moss", Fix(Coordinates(47.6, -122.4), 12.0)),
        )
        first.close()

        val second = Room.databaseBuilder(context, ForagerDatabase::class.java, name).build()
        val read = RoomJournalStore(second.journalDao()).all()
        second.close()

        val entries = (read as Outcome.Ok).value
        assertEquals(1, entries.size)
        assertEquals("in the moss", entries.single().notes)
        assertEquals(CHANTERELLE, entries.single().species)
        assertEquals(Fix(Coordinates(47.6, -122.4), 12.0), entries.single().where)
    }
}
