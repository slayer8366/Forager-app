package com.zynergy.forager.domain

import com.zynergy.forager.domain.usecase.AssessPlanTiming
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun seasonality(counts: Map<Month, Int>, species: Species = CHANTERELLE) =
    Seasonality(counts, PUGET_SOUND, species)

class SeasonalityModelTest {

    @Test
    fun `a year missing a month is refused rather than silently treated as zero`() {
        val eleven = Month.entries.filterNot { it == Month.APRIL }.associateWith { 1 }
        val error = assertFailsWith<IllegalArgumentException> { seasonality(eleven) }
        assertTrue(error.message!!.contains("twelve months"))
    }

    @Test
    fun `the busiest month is the one with the most records`() {
        val s = seasonality(yearPeaking(Month.OCTOBER, peak = 226, rest = 10))
        assertEquals(Month.OCTOBER, s.busiestMonth)
    }

    @Test
    fun `an empty year has no busiest month rather than defaulting to January`() {
        val s = seasonality(flatYear(0))
        assertNull(s.busiestMonth)
        assertEquals(0, s.total)
    }

    @Test
    fun `share in a month is a share of records, and is zero when nothing was recorded`() {
        // 75 in October, 25 in September, nothing else: 100 records, so October holds three quarters.
        val counts = Month.entries.associateWith {
            when (it) {
                Month.OCTOBER -> 75
                Month.SEPTEMBER -> 25
                else -> 0
            }
        }
        val s = seasonality(counts)
        assertEquals(0.75, s.shareIn(Month.OCTOBER))
        assertEquals(0.25, s.shareIn(Month.SEPTEMBER))
        assertEquals(0.0, s.shareIn(Month.JUNE))
        assertEquals(0.0, seasonality(flatYear(0)).shareIn(Month.OCTOBER))
    }

    @Test
    fun `months covering a share come back busiest first`() {
        val counts = mapOf(
            Month.SEPTEMBER to 164, Month.OCTOBER to 226, Month.NOVEMBER to 179,
        ).let { peaks -> Month.entries.associateWith { peaks[it] ?: 0 } }
        val covering = seasonality(counts).monthsCovering(0.75)

        assertEquals(listOf(Month.OCTOBER, Month.NOVEMBER, Month.SEPTEMBER), covering)
    }

    @Test
    fun `a handful of records is not enough to call a pattern`() {
        val threeRecords = Month.entries.associateWith {
            if (it in listOf(Month.OCTOBER, Month.NOVEMBER, Month.DECEMBER)) 1 else 0
        }
        val s = seasonality(threeRecords)

        assertEquals(3, s.total)
        assertFalse(
            s.hasEnoughRecordsForPattern,
            "three bars of one record each look exactly like a seasonal signal",
        )
    }

    @Test
    fun `at the stated threshold a pattern is allowed`() {
        val atThreshold = Month.entries.associateWith {
            if (it == Month.OCTOBER) Seasonality.MIN_RECORDS_FOR_PATTERN else 0
        }
        assertTrue(seasonality(atThreshold).hasEnoughRecordsForPattern)
    }

    @Test
    fun `an empty year cannot say which months matter, and says null rather than an empty list`() {
        assertNull(seasonality(flatYear(0)).monthsCovering(0.75))
    }
}

class TargetTimingTest {

    @Test
    fun `peak month is recognised`() {
        val t = TargetTiming.from(seasonality(yearPeaking(Month.OCTOBER, 200, 5)), Month.OCTOBER)
        assertTrue(t.isPeakMonth)
    }

    @Test
    fun `a month with no records is not the peak and is flagged as having no evidence`() {
        val t = TargetTiming.from(seasonality(flatYear(0)), Month.OCTOBER)
        assertFalse(t.isPeakMonth)
        assertTrue(t.hasNoRecords)
        assertEquals(0.0, t.shareInChosenMonth)
    }
}

class AssessPlanTimingTest {

    private val criteria = PlanCriteria(
        area = PUGET_SOUND,
        date = LocalDate.of(2026, 10, 12),
        targets = listOf(CHANTERELLE, MOREL),
    )

    @Test
    fun `targets come back ordered by how well the chosen month fits`() = runTest {
        val catalog = FakeCatalog(
            seasonalityBySpecies = mapOf(
                CHANTERELLE.catalogId to Outcome.Ok(
                    Seasonality(yearPeaking(Month.OCTOBER, 200, 0), PUGET_SOUND, CHANTERELLE),
                ),
                MOREL.catalogId to Outcome.Ok(
                    Seasonality(yearPeaking(Month.APRIL, 200, 0), PUGET_SOUND, MOREL),
                ),
            ),
        )
        val result = assertIs<Outcome.Ok<List<TargetTiming>>>(AssessPlanTiming(catalog)(criteria))

        assertEquals(listOf(CHANTERELLE, MOREL), result.value.map { it.species })
        assertTrue(result.value.first().isPeakMonth)
        assertFalse(result.value.last().isPeakMonth)
    }

    @Test
    fun `one failing target does not sink the others, and is named`() = runTest {
        val catalog = FakeCatalog(
            seasonalityBySpecies = mapOf(
                CHANTERELLE.catalogId to Outcome.Ok(
                    Seasonality(yearPeaking(Month.OCTOBER, 50, 0), PUGET_SOUND, CHANTERELLE),
                ),
                MOREL.catalogId to Outcome.Failed("iNaturalist returned HTTP 503"),
            ),
        )
        val result = assertIs<Outcome.Partial<List<TargetTiming>>>(AssessPlanTiming(catalog)(criteria))

        assertEquals(1, result.value.size)
        assertTrue(result.note.contains("Common Morel"), result.note)
        assertTrue(result.note.contains("503"), result.note)
    }

    @Test
    fun `when every target fails the whole assessment fails`() = runTest {
        val catalog = FakeCatalog(
            seasonalityBySpecies = mapOf(
                CHANTERELLE.catalogId to Outcome.Failed("down"),
                MOREL.catalogId to Outcome.Failed("down"),
            ),
        )
        assertIs<Outcome.Failed>(AssessPlanTiming(catalog)(criteria))
    }

    @Test
    fun `no targets means no lookups at all`() = runTest {
        val catalog = FakeCatalog()
        val result = AssessPlanTiming(catalog)(criteria.copy(targets = emptyList()))

        assertIs<Outcome.Ok<List<TargetTiming>>>(result)
        assertEquals(0, catalog.seasonalityCalls)
    }

    @Test
    fun `a target with no records is kept, because that is an answer too`() = runTest {
        val catalog = FakeCatalog(
            seasonalityBySpecies = mapOf(
                CHANTERELLE.catalogId to Outcome.Ok(Seasonality(flatYear(0), PUGET_SOUND, CHANTERELLE)),
                MOREL.catalogId to Outcome.Ok(Seasonality(flatYear(0), PUGET_SOUND, MOREL)),
            ),
        )
        val result = assertIs<Outcome.Ok<List<TargetTiming>>>(AssessPlanTiming(catalog)(criteria))

        assertEquals(2, result.value.size)
        assertTrue(result.value.all { it.hasNoRecords })
    }
}

class PlanCriteriaTest {

    private val base = PlanCriteria(PUGET_SOUND, LocalDate.of(2026, 10, 12))

    @Test
    fun `adding the same target twice does not duplicate it`() {
        val once = base.withTarget(CHANTERELLE).withTarget(CHANTERELLE)
        assertEquals(1, once.targets.size)
    }

    @Test
    fun `a target can be removed by its catalog id`() {
        val removed = base.withTarget(CHANTERELLE).withTarget(MOREL).withoutTarget(CHANTERELLE)
        assertEquals(listOf(MOREL), removed.targets)
    }

    @Test
    fun `the month comes from the chosen date`() {
        assertEquals(Month.OCTOBER, base.month)
    }
}
