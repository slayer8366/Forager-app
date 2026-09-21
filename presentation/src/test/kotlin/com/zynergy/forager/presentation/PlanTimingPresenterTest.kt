package com.zynergy.forager.presentation

import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.PlanCriteria
import com.zynergy.forager.domain.Seasonality
import com.zynergy.forager.domain.Species
import com.zynergy.forager.domain.TaxonRank
import com.zynergy.forager.domain.port.SpeciesCatalog
import com.zynergy.forager.domain.port.TerrainSource
import com.zynergy.forager.domain.usecase.AssessPlanTiming
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val AREA = BoundingBox(south = 47.0, west = -123.0, north = 47.9, east = -122.1)
private val CHANT = Species("47348", "Cantharellus cibarius", "Golden Chanterelle", TaxonRank.SPECIES)
private val OCTOBER_12 = LocalDate.of(2026, 10, 12)

private fun year(peak: Month, count: Int) = Month.entries.associateWith { if (it == peak) count else 0 }

private class CatalogStub(private val outcome: Outcome<Seasonality>) : SpeciesCatalog {
    override suspend fun search(query: String, limit: Int): Outcome<List<Species>> = Outcome.Ok(emptyList())
    override suspend fun recordedIn(area: BoundingBox, limit: Int): Outcome<List<Species>> = Outcome.Ok(emptyList())
    override suspend fun seasonality(species: Species, area: BoundingBox): Outcome<Seasonality> = outcome
}

class PlanTimingPresenterTest {

    private val criteria = PlanCriteria(AREA, OCTOBER_12, listOf(CHANT))

    @Test
    fun `a complete assessment carries the timings and no notice`() = runTest {
        val catalog = CatalogStub(Outcome.Ok(Seasonality(year(Month.OCTOBER, 200), AREA, CHANT)))
        val state = PlanTimingPresenter(AssessPlanTiming(catalog)).assess(criteria)

        assertEquals(1, state.timings.size)
        assertNull(state.notice)
        assertTrue(state.timings.single().isPeakMonth)
    }

    @Test
    fun `a target that could not be looked up leaves an incomplete notice`() = runTest {
        val catalog = CatalogStub(Outcome.Failed("iNaturalist returned HTTP 503"))
        val state = PlanTimingPresenter(AssessPlanTiming(catalog)).assess(criteria)

        // Every target failed, so this is a problem rather than an incomplete list.
        assertIs<Notice.Problem>(state.notice)
        assertTrue(state.timings.isEmpty())
    }

    @Test
    fun `assessing with no targets is complete and empty, not an error`() = runTest {
        val catalog = CatalogStub(Outcome.Ok(Seasonality(year(Month.OCTOBER, 1), AREA, CHANT)))
        val state = PlanTimingPresenter(AssessPlanTiming(catalog))
            .assess(criteria.copy(targets = emptyList()))

        assertTrue(state.timings.isEmpty())
        assertNull(state.notice)
        assertTrue(state.assessed)
    }
}

class SeasonalityPresenterTest {

    @Test
    fun `a partial year is shown with its caveat rather than dropped`() = runTest {
        val season = Seasonality(year(Month.OCTOBER, 5), AREA, CHANT)
        val catalog = CatalogStub(Outcome.Partial(season, "one bucket was unreadable"))

        val state = SeasonalityPresenter(catalog).load(CHANT, PlanCriteria(AREA, OCTOBER_12))

        assertNotNull(state.seasonality)
        assertIs<Notice.Incomplete>(state.notice)
    }
}

/**
 * These are the tests that keep the honest answer honest. If someone later makes the terrain source
 * return estimates, these fail and the change has to be deliberate.
 */
class ConditionsPresenterTest {

    private class RefusingTerrain : TerrainSource {
        override suspend fun soil(area: BoundingBox) = Outcome.Unsupported("soil data")
        override suspend fun terrain(area: BoundingBox) = Outcome.Unsupported("terrain data")
        override suspend fun fruitingLagDays(species: Species, area: BoundingBox, onOrAbout: LocalDate) =
            Outcome.Unsupported("rain-to-fruiting lag (needs a model)")
    }

    @Test
    fun `soil, terrain and lag each report as unavailable with a stated reason`() = runTest {
        val state = ConditionsPresenter(RefusingTerrain())
            .load(PlanCriteria(AREA, OCTOBER_12), CHANT, OCTOBER_12)

        assertIs<Notice.NotAvailable>(state.soil)
        assertIs<Notice.NotAvailable>(state.terrain)
        val lag = assertIs<Notice.NotAvailable>(state.fruitingLag)
        assertTrue(lag.capability.contains("model"), lag.capability)
    }

    @Test
    fun `no lag value is produced when the source cannot supply one`() = runTest {
        val state = ConditionsPresenter(RefusingTerrain())
            .load(PlanCriteria(AREA, OCTOBER_12), CHANT, OCTOBER_12)

        assertNull(state.lagDays, "an unavailable lag must not become a number on screen")
    }

    @Test
    fun `with no target chosen the lag says it needs one`() = runTest {
        val state = ConditionsPresenter(RefusingTerrain())
            .load(PlanCriteria(AREA, OCTOBER_12), null, OCTOBER_12)

        val lag = assertIs<Notice.NotAvailable>(state.fruitingLag)
        assertTrue(lag.capability.contains("target species"), lag.capability)
    }
}
