package com.zynergy.forager.presentation

import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.Seasonality
import com.zynergy.forager.domain.Species
import com.zynergy.forager.domain.TaxonRank
import com.zynergy.forager.domain.TripPlan
import com.zynergy.forager.domain.port.Clock
import com.zynergy.forager.domain.port.IdSource
import com.zynergy.forager.domain.port.SpeciesCatalog
import com.zynergy.forager.domain.port.TripPlanStore
import com.zynergy.forager.domain.usecase.PlanTrip
import com.zynergy.forager.domain.usecase.SuggestTargets
import com.zynergy.forager.domain.usecase.UpcomingPlans
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val AREA = BoundingBox(south = 47.0, west = -123.0, north = 47.9, east = -122.1)
private val TODAY = LocalDate.of(2026, 9, 20)
private val TURKEY_TAIL = Species("48435", "Trametes versicolor", "turkey-tail", TaxonRank.SPECIES)

private class ListStore(var failReads: Boolean = false) : TripPlanStore {
    val plans = mutableListOf<TripPlan>()
    override suspend fun save(plan: TripPlan): Outcome<TripPlan> {
        plans.removeAll { it.id == plan.id }
        plans += plan
        return Outcome.Ok(plan)
    }
    override suspend fun upcoming(from: LocalDate): Outcome<List<TripPlan>> =
        if (failReads) Outcome.Failed("disk unreadable") else Outcome.Ok(plans.filter { !it.date.isBefore(from) })
}

private object Today : Clock {
    override fun now(): Instant = Instant.parse("2026-09-20T12:00:00Z")
    override fun today(): LocalDate = TODAY
}

private class Ids : IdSource { private var n = 0; override fun newId() = "plan-${++n}" }

private object NoCatalog : SpeciesCatalog {
    override suspend fun search(query: String, limit: Int) = Outcome.Unsupported("search")
    override suspend fun recordedIn(area: BoundingBox, limit: Int) = Outcome.Unsupported("area search")
    override suspend fun seasonality(species: Species, area: BoundingBox): Outcome<Seasonality> =
        Outcome.Unsupported("seasonality")
}

private fun presenter(store: ListStore) =
    TripPlannerPresenter(SuggestTargets(NoCatalog), PlanTrip(store, Today, Ids()), UpcomingPlans(store, Today))

class TripPlannerPresenterTest {

    @Test
    fun `a saved plan comes back in the upcoming list`() = runTest {
        val store = ListStore()
        val p = presenter(store)

        val saved = p.save("Autumn walk", TODAY.plusDays(14), AREA, listOf(TURKEY_TAIL))
        val list = p.upcoming()

        assertEquals("Autumn walk", saved.saved?.name)
        assertNull(saved.notice)
        assertEquals(listOf("Autumn walk"), list.plans.map { it.name })
        assertEquals(listOf(TURKEY_TAIL), list.plans.single().targets)
    }

    @Test
    fun `a plan with no name is refused with the reason, and nothing is stored`() = runTest {
        val store = ListStore()
        val result = presenter(store).save("  ", TODAY.plusDays(1), AREA, emptyList())

        assertNull(result.saved)
        assertEquals("a plan needs a name", assertIs<Notice.Problem>(result.notice).detail)
        assertTrue(store.plans.isEmpty())
    }

    @Test
    fun `upcoming plans are listed soonest first`() = runTest {
        val store = ListStore()
        val p = presenter(store)
        p.save("Later", TODAY.plusDays(30), AREA, emptyList())
        p.save("Sooner", TODAY.plusDays(2), AREA, emptyList())

        assertEquals(listOf("Sooner", "Later"), p.upcoming().plans.map { it.name })
    }

    @Test
    fun `a failed read is a problem, not an empty list that looks like no plans`() = runTest {
        val state = presenter(ListStore(failReads = true)).upcoming()

        assertTrue(state.plans.isEmpty())
        assertEquals("disk unreadable", assertIs<Notice.Problem>(state.notice).detail)
    }
}
