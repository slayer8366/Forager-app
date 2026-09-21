package com.zynergy.forager.presentation

import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.Species
import com.zynergy.forager.domain.TaxonRank
import com.zynergy.forager.domain.port.AreaSightingChance
import com.zynergy.forager.domain.port.CellSightingChance
import com.zynergy.forager.domain.port.ForecastSource
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val AREA = BoundingBox(south = 47.0, west = -123.0, north = 47.9, east = -122.1)
private val CANTHARELLUS = Species("47348", "Cantharellus", "chanterelles", TaxonRank.GENUS)
private val ON = LocalDate.of(2026, 10, 14)
private val WEEK = LocalDate.of(2026, 10, 12)
private val CELL = BoundingBox(south = 47.0, west = -123.0, north = 47.1, east = -122.9)

private class Refusing : ForecastSource {
    override suspend fun sightingChance(group: Species, area: BoundingBox, on: LocalDate) =
        Outcome.Unsupported("a sighting-chance forecast (none is published yet for any area)")
}

private class Canned(private val outcome: Outcome<AreaSightingChance>) : ForecastSource {
    override suspend fun sightingChance(group: Species, area: BoundingBox, on: LocalDate) = outcome
}

private fun cell(chance: Double, applicable: Boolean = true) =
    CellSightingChance(CELL, chance, chance - 0.05..chance + 0.05, applicable)

private fun area(vararg cells: CellSightingChance) =
    AreaSightingChance(CANTHARELLUS, WEEK, WEEK.plusDays(2), cells.toList())

/**
 * The tests that keep the gap honest. If a source starts answering with numbers, the change has to
 * be deliberate, and the words around the number are fixed here.
 */
class SightingChancePresenterTest {

    @Test
    fun `with nothing published the line says so and shows no number`() = runTest {
        val state = SightingChancePresenter(Refusing()).load(CANTHARELLUS, AREA, ON)

        val notice = assertIs<Notice.NotAvailable>(state.notice)
        assertTrue(notice.capability.contains("none is published yet"), notice.capability)
        assertNull(state.line)
        assertNull(state.referenceClass)
    }

    @Test
    fun `with no target it asks for one rather than asking the source`() = runTest {
        var asked = false
        val spy = object : ForecastSource {
            override suspend fun sightingChance(group: Species, area: BoundingBox, on: LocalDate): Outcome<AreaSightingChance> {
                asked = true
                return Outcome.Unsupported("x")
            }
        }
        val state = SightingChancePresenter(spy).load(null, AREA, ON)

        assertIs<Notice.NotAvailable>(state.notice)
        assertEquals(false, asked)
    }

    @Test
    fun `a scored area shows the range across its cells, never an average, with the reference class`() = runTest {
        val state = SightingChancePresenter(Canned(Outcome.Ok(area(cell(0.12), cell(0.31), cell(0.9, applicable = false)))))
            .load(CANTHARELLUS, AREA, ON)

        assertEquals("Sighting chance for chanterelles: 12% to 31% across 2 cells", state.line)
        assertEquals(SIGHTING_CHANCE_REFERENCE_CLASS, state.referenceClass)
        assertEquals("Week of 2026-10-12, weather to 2026-10-14", state.dates)
        assertNull(state.notice)
    }

    @Test
    fun `an area whose cells are all masked has no forecast, not a zero`() = runTest {
        val state = SightingChancePresenter(Canned(Outcome.Ok(area(cell(0.4, applicable = false)))))
            .load(CANTHARELLUS, AREA, ON)

        assertNull(state.line)
        val notice = assertIs<Notice.NotAvailable>(state.notice)
        assertTrue(notice.capability.contains("no cell here is scored"), notice.capability)
    }

    @Test
    fun `a partial answer keeps its note beside the numbers`() = runTest {
        val state = SightingChancePresenter(Canned(Outcome.Partial(area(cell(0.2)), "3 of 4 cells are masked")))
            .load(CANTHARELLUS, AREA, ON)

        assertEquals("Sighting chance for chanterelles: 20% across 1 cell", state.line)
        assertEquals("3 of 4 cells are masked", assertIs<Notice.Incomplete>(state.notice).detail)
    }

    /** The forecast project's R8, applied to this app's copy: zero hits for the forbidden terms. */
    @Test
    fun `no forecast copy calls the number a fruiting probability or a chance of finding`() = runTest {
        val states = listOf(
            SightingChancePresenter(Refusing()).load(CANTHARELLUS, AREA, ON),
            SightingChancePresenter(Refusing()).load(null, AREA, ON),
            SightingChancePresenter(Canned(Outcome.Ok(area(cell(0.5))))).load(CANTHARELLUS, AREA, ON),
            SightingChancePresenter(Canned(Outcome.Ok(area(cell(0.5, applicable = false))))).load(CANTHARELLUS, AREA, ON),
        )
        val copy = states.flatMap { s ->
            listOfNotNull(
                s.line, s.referenceClass, s.dates,
                when (val n = s.notice) {
                    is Notice.Incomplete -> n.detail
                    is Notice.Problem -> n.detail
                    is Notice.NotAvailable -> n.capability
                    null -> null
                },
            )
        }
        for (text in copy) for (term in FORBIDDEN_FORECAST_TERMS) {
            assertTrue(!text.contains(term, ignoreCase = true), "\"$text\" contains \"$term\"")
        }
    }
}
