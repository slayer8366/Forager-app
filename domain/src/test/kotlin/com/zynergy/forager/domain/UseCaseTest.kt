package com.zynergy.forager.domain

import com.zynergy.forager.domain.usecase.PlanTrip
import com.zynergy.forager.domain.usecase.UpcomingPlans
import com.zynergy.forager.domain.usecase.RecordSighting
import com.zynergy.forager.domain.usecase.SearchSpecies
import com.zynergy.forager.domain.usecase.SuggestTargets
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val NOW = Instant.parse("2026-09-20T08:00:00Z")
private val TODAY = LocalDate.of(2026, 9, 20)

class SearchSpeciesTest {

    @Test
    fun `a one-character query is refused without asking the catalog`() = runTest {
        val catalog = FakeCatalog()
        val result = SearchSpecies(catalog)("c")

        assertIs<Outcome.Failed>(result)
        assertEquals(0, catalog.searchCalls, "the catalog must not be asked at all")
    }

    @Test
    fun `whitespace does not make a query long enough`() = runTest {
        val catalog = FakeCatalog()
        assertIs<Outcome.Failed>(SearchSpecies(catalog)(" c "))
        assertEquals(0, catalog.searchCalls)
    }

    @Test
    fun `a query is trimmed before it reaches the catalog`() = runTest {
        val catalog = FakeCatalog()
        SearchSpecies(catalog)("  chanterelle  ")
        assertEquals("chanterelle", catalog.lastQuery)
    }

    @Test
    fun `this app's ceiling applies even when the caller asks for more`() = runTest {
        val catalog = FakeCatalog()
        SearchSpecies(catalog)("chanterelle", limit = 500)
        assertEquals(SearchSpecies.MAX_RESULTS, catalog.lastLimit)
    }

    @Test
    fun `a partial result stays partial instead of reading as complete`() = runTest {
        val catalog = FakeCatalog(
            searchResult = Outcome.Partial(listOf(CHANTERELLE), "offline cache only"),
        )
        val result = SearchSpecies(catalog)("chanterelle")

        val partial = assertIs<Outcome.Partial<List<Species>>>(result)
        assertEquals("offline cache only", partial.note)
    }
}

class RecordSightingTest {

    @Test
    fun `an empty form is refused and nothing is stored`() = runTest {
        val store = FakeJournalStore()
        val result = RecordSighting(store, FixedClock(NOW, TODAY), SequentialIds())(
            identification = Identification.Unidentified,
            notes = "",
        )

        assertIs<Outcome.Failed>(result)
        assertEquals(0, store.saved.size)
    }

    @Test
    fun `a sighting takes its time from the clock and its id from the id source`() = runTest {
        val store = FakeJournalStore()
        val result = RecordSighting(store, FixedClock(NOW, TODAY), SequentialIds("entry-"))(
            identification = Identification.Taxon(CHANTERELLE, TaxonSource.SEARCH),
            where = fixAt(47.5, -122.5),
        )

        val ok = assertIs<Outcome.Ok<JournalEntry>>(result)
        assertEquals("entry-1", ok.value.id)
        assertEquals(NOW, ok.value.recordedAt)
        assertEquals(1, store.saved.size)
    }

    @Test
    fun `a find with no name is stored when it has notes`() = runTest {
        val store = FakeJournalStore()
        val result = RecordSighting(store, FixedClock(NOW, TODAY), SequentialIds())(
            identification = Identification.Unidentified,
            notes = "small brown, under alder",
        )

        assertIs<Outcome.Ok<JournalEntry>>(result)
        assertEquals(1, store.saved.size)
    }
}

class PlanTripTest {

    private fun planner(store: FakeTripPlanStore) =
        PlanTrip(store, FixedClock(NOW, TODAY), SequentialIds("trip-"))

    @Test
    fun `a plan dated yesterday is refused and nothing is stored`() = runTest {
        val store = FakeTripPlanStore()
        val result = planner(store)("Autumn walk", TODAY.minusDays(1), PUGET_SOUND, listOf(CHANTERELLE))

        val failed = assertIs<Outcome.Failed>(result)
        assertTrue(failed.reason.contains("in the past"))
        assertEquals(0, store.saved.size)
    }

    @Test
    fun `today is allowed`() = runTest {
        val store = FakeTripPlanStore()
        assertIs<Outcome.Ok<TripPlan>>(planner(store)("Today", TODAY, PUGET_SOUND, listOf(CHANTERELLE)))
    }

    @Test
    fun `a duplicated target is named in the refusal`() = runTest {
        val store = FakeTripPlanStore()
        val result = planner(store)("Dupes", TODAY, PUGET_SOUND, listOf(CHANTERELLE, CHANTERELLE))

        val failed = assertIs<Outcome.Failed>(result)
        assertTrue(failed.reason.contains("Golden Chanterelle"))
        assertEquals(0, store.saved.size)
    }

    @Test
    fun `a blank name is refused`() = runTest {
        val store = FakeTripPlanStore()
        assertIs<Outcome.Failed>(planner(store)("   ", TODAY, PUGET_SOUND, emptyList()))
    }

    @Test
    fun `the stored name is trimmed`() = runTest {
        val store = FakeTripPlanStore()
        planner(store)("  Hood Canal  ", TODAY, PUGET_SOUND, listOf(MOREL))
        assertEquals("Hood Canal", store.saved.single().name)
    }

    @Test
    fun `a plan covers an entry inside its area and not one outside`() = runTest {
        val store = FakeTripPlanStore()
        val ok = assertIs<Outcome.Ok<TripPlan>>(
            planner(store)("Walk", TODAY, PUGET_SOUND, listOf(CHANTERELLE)),
        )
        val inside = entryOf("a", NOW, CHANTERELLE, "", fixAt(47.5, -122.5))
        val outside = entryOf("b", NOW, CHANTERELLE, "", fixAt(40.0, -100.0))
        val noFix = entryOf("c", NOW, CHANTERELLE, "", null)

        assertTrue(ok.value.covers(inside))
        assertTrue(!ok.value.covers(outside))
        assertTrue(!ok.value.covers(noFix))
    }
}

class SuggestTargetsTest {

    @Test
    fun `an unsupported catalog says so rather than returning an empty list`() = runTest {
        val catalog = FakeCatalog(areaResult = Outcome.Unsupported("range data"))
        val result = SuggestTargets(catalog)(PUGET_SOUND)

        val unsupported = assertIs<Outcome.Unsupported>(result)
        assertEquals("range data", unsupported.capability)
    }

    @Test
    fun `the suggestion ceiling applies`() = runTest {
        val catalog = FakeCatalog()
        SuggestTargets(catalog)(PUGET_SOUND, limit = 10_000)
        assertEquals(SuggestTargets.MAX_SUGGESTIONS, catalog.lastLimit)
    }
}

class UpcomingPlansTest {

    private val today = java.time.LocalDate.of(2026, 9, 20)
    private val clock = FixedClock(java.time.Instant.parse("2026-09-20T12:00:00Z"), today)

    private fun plan(id: String, date: java.time.LocalDate) =
        TripPlan(id, "trip $id", date, PUGET_SOUND, emptyList())

    @Test
    fun `plans dated today count as upcoming and past ones do not`() = runTest {
        val store = FakeTripPlanStore().apply {
            save(plan("yesterday", today.minusDays(1)))
            save(plan("today", today))
            save(plan("next-week", today.plusDays(7)))
        }

        val ok = assertIs<Outcome.Ok<List<TripPlan>>>(UpcomingPlans(store, clock)())

        assertEquals(setOf("today", "next-week"), ok.value.map { it.id }.toSet())
    }
}
