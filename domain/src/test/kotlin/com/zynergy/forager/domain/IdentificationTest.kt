package com.zynergy.forager.domain

import com.zynergy.forager.domain.usecase.PlanTrip
import com.zynergy.forager.domain.usecase.RecordSighting
import com.zynergy.forager.domain.usecase.ReidentifyEntry
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val RECORDED = Instant.parse("2026-09-20T08:00:00Z")
private val LATER = Instant.parse("2026-09-21T19:30:00Z")
private val TODAY = LocalDate.of(2026, 9, 20)

class IdentificationModelTest {

    @Test
    fun `a blank typed name is not an unconfirmed name`() {
        assertFailsWith<IllegalArgumentException> { Identification.Unconfirmed("   ") }
    }

    @Test
    fun `an entry cannot be built with no identification at all`() {
        val error = assertFailsWith<IllegalArgumentException> {
            JournalEntry("e1", RECORDED, emptyList(), "a note", null)
        }
        assertTrue(error.message!!.contains("even if it is Unidentified"))
    }

    @Test
    fun `the current identification is the latest change and the earlier ones stay readable`() {
        val first = IdentificationChange(Identification.Unconfirmed("chanterelle?"), RECORDED)
        val second = IdentificationChange(Identification.Taxon(CHANTERELLE, TaxonSource.SEARCH), LATER)
        val entry = JournalEntry("e1", RECORDED, listOf(first, second), "", null)

        assertEquals(Identification.Taxon(CHANTERELLE, TaxonSource.SEARCH), entry.identification)
        assertEquals(CHANTERELLE, entry.species)
        assertEquals(listOf(first), entry.earlierIdentifications)
    }

    @Test
    fun `a typed name is not a species`() {
        val entry = JournalEntry.first("e1", RECORDED, Identification.Unconfirmed("salmon"), "", null)
        assertNull(entry.species)
    }

    @Test
    fun `an entry once named and later marked unidentified still records something`() {
        val entry = JournalEntry(
            "e1",
            RECORDED,
            listOf(
                IdentificationChange(Identification.Taxon(CHANTERELLE, TaxonSource.SEARCH), RECORDED),
                IdentificationChange(Identification.Unidentified, LATER),
            ),
            notes = "",
            where = null,
        )
        assertEquals(Identification.Unidentified, entry.identification)
    }

    @Test
    fun `history keeps the order of the changes even when the clock went backwards`() {
        val changes = listOf(
            IdentificationChange(Identification.Unconfirmed("first guess"), LATER),
            IdentificationChange(Identification.Taxon(MOREL, TaxonSource.SEARCH), RECORDED),
        )
        val entry = JournalEntry("e1", RECORDED, changes, "", null)
        assertEquals(MOREL, entry.species)
    }
}

class RecordIdentifiedSightingTest {

    private fun recorder(store: FakeJournalStore) = RecordSighting(store, FixedClock(RECORDED, TODAY), SequentialIds())

    @Test
    fun `a typed name is saved exactly as typed, with no note needed`() = runTest {
        val store = FakeJournalStore()
        val result = recorder(store)(identification = Identification.Unconfirmed("salmon "))

        val saved = assertIs<Outcome.Ok<JournalEntry>>(result).value
        assertEquals(Identification.Unconfirmed("salmon "), saved.identification)
        assertEquals(listOf(IdentificationChange(Identification.Unconfirmed("salmon "), RECORDED)), saved.identifications)
    }

    @Test
    fun `unidentified with no note is refused and nothing is stored`() = runTest {
        val store = FakeJournalStore()
        val result = recorder(store)(identification = Identification.Unidentified, notes = " ")

        assertIs<Outcome.Failed>(result)
        assertEquals(0, store.saved.size)
    }

    @Test
    fun `a taxon at genus rank is a taxon like any other`() = runTest {
        val russula = Species("47578", "Russula", "brittlegills", TaxonRank.GENUS)
        val store = FakeJournalStore()
        val result = recorder(store)(identification = Identification.Taxon(russula, TaxonSource.SEARCH))

        assertEquals(russula, assertIs<Outcome.Ok<JournalEntry>>(result).value.species)
    }
}

class ReidentifyEntryTest {

    @Test
    fun `a change is appended with its own time and the earlier identification kept`() = runTest {
        val store = FakeJournalStore()
        val original = assertIs<Outcome.Ok<JournalEntry>>(
            RecordSighting(store, FixedClock(RECORDED, TODAY), SequentialIds())(
                identification = Identification.Unidentified,
                notes = "orange, under fir",
            ),
        ).value

        val changed = ReidentifyEntry(store, FixedClock(LATER, TODAY))(
            original,
            Identification.Taxon(CHANTERELLE, TaxonSource.SEARCH),
        )

        val entry = assertIs<Outcome.Ok<JournalEntry>>(changed).value
        assertEquals(
            listOf(
                IdentificationChange(Identification.Unidentified, RECORDED),
                IdentificationChange(Identification.Taxon(CHANTERELLE, TaxonSource.SEARCH), LATER),
            ),
            entry.identifications,
        )
    }

    @Test
    fun `choosing the current identification again is refused and adds nothing`() = runTest {
        val store = FakeJournalStore()
        val original = assertIs<Outcome.Ok<JournalEntry>>(
            RecordSighting(store, FixedClock(RECORDED, TODAY), SequentialIds())(
                identification = Identification.Taxon(CHANTERELLE, TaxonSource.PLAN_TARGET),
            ),
        ).value

        val again = ReidentifyEntry(store, FixedClock(LATER, TODAY))(
            original,
            Identification.Taxon(CHANTERELLE, TaxonSource.SEARCH),
        )

        assertIs<Outcome.Failed>(again)
        assertEquals(1, store.saved.single().identifications.size)
    }
}

class EditAndDuplicatePlanTest {

    private fun planner(store: FakeTripPlanStore, ids: SequentialIds = SequentialIds("plan-")) =
        PlanTrip(store, FixedClock(RECORDED, TODAY), ids)

    private suspend fun savedPlan(store: FakeTripPlanStore): TripPlan = assertIs<Outcome.Ok<TripPlan>>(
        planner(store)("Autumn walk", TODAY.plusDays(7), PUGET_SOUND, listOf(CHANTERELLE)),
    ).value

    @Test
    fun `an edit keeps the plan's id and replaces it rather than adding a second`() = runTest {
        val store = FakeTripPlanStore()
        val original = savedPlan(store)

        val edited = planner(store).update(
            original.id, "Autumn walk, north end", TODAY.plusDays(8), PUGET_SOUND, listOf(MOREL),
        )

        val plan = assertIs<Outcome.Ok<TripPlan>>(edited).value
        assertEquals(original.id, plan.id)
        assertEquals(listOf(plan), store.saved)
    }

    @Test
    fun `an edit to a past date is refused and the stored plan is left as it was`() = runTest {
        val store = FakeTripPlanStore()
        val original = savedPlan(store)

        val edited = planner(store).update(original.id, "Moved", TODAY.minusDays(1), PUGET_SOUND, emptyList())

        assertIs<Outcome.Failed>(edited)
        assertEquals(listOf(original), store.saved)
    }

    @Test
    fun `a duplicate gets a new id and a copy name, and the original is untouched`() = runTest {
        val store = FakeTripPlanStore()
        val ids = SequentialIds("plan-")
        val original = assertIs<Outcome.Ok<TripPlan>>(
            planner(store, ids)("Autumn walk", TODAY.plusDays(7), PUGET_SOUND, listOf(CHANTERELLE)),
        ).value

        val copy = assertIs<Outcome.Ok<TripPlan>>(planner(store, ids).duplicate(original)).value

        assertEquals("plan-2", copy.id)
        assertEquals("Autumn walk (copy)", copy.name)
        assertEquals(original.copy(id = copy.id, name = copy.name), copy)
        assertEquals(listOf(original, copy), store.saved)
    }
}
