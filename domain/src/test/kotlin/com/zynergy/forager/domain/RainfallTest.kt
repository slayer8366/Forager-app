package com.zynergy.forager.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val DAY0 = LocalDate.of(2026, 9, 7)

private fun window(vararg mm: Double?) = RainfallWindow(
    days = mm.mapIndexed { i, v -> DailyRainfall(DAY0.plusDays(i.toLong()), v) },
    area = PUGET_SOUND,
)

class DailyRainfallTest {

    @Test
    fun `a missing reading is not the same as zero`() {
        assertTrue(DailyRainfall(DAY0, null).isMissing)
        assertFalse(DailyRainfall(DAY0, 0.0).isMissing)
    }

    @Test
    fun `negative rainfall is refused`() {
        assertFailsWith<IllegalArgumentException> { DailyRainfall(DAY0, -1.0) }
    }
}

class RainfallWindowTest {

    @Test
    fun `days must be consecutive and ascending`() {
        val outOfOrder = listOf(
            DailyRainfall(DAY0.plusDays(1), 1.0),
            DailyRainfall(DAY0, 1.0),
        )
        assertFailsWith<IllegalArgumentException> { RainfallWindow(outOfOrder, PUGET_SOUND) }
    }

    @Test
    fun `a missing day is never summed as zero, and the gap is reported`() {
        val w = window(10.0, null, 5.0)

        assertEquals(15.0, w.totalMillimetres)
        assertEquals(1, w.missingDays)
        assertTrue(w.hasGaps)
    }

    @Test
    fun `a window with no gaps says so`() {
        val w = window(1.0, 0.0, 2.0)
        assertFalse(w.hasGaps)
        assertEquals(0, w.missingDays)
        assertEquals(3.0, w.totalMillimetres)
    }

    @Test
    fun `days since a wetting rain counts back from the last day`() {
        // 22mm fell four days before the end of the window.
        val w = window(0.0, 22.0, 0.5, 0.3, 0.0)
        assertEquals(3, w.daysSinceWettingRain())
    }

    @Test
    fun `rain on the last day is zero days ago`() {
        assertEquals(0, window(0.0, 0.0, 9.0).daysSinceWettingRain())
    }

    @Test
    fun `no wetting rain in the window is null, not zero`() {
        assertNull(
            window(0.1, 0.0, 0.4).daysSinceWettingRain(),
            "zero would read as 'it rained today'",
        )
    }

    @Test
    fun `a gap after the last wetting rain makes the answer unknowable`() {
        // 20mm, then a day nobody has a reading for: it may have rained harder since.
        assertNull(window(20.0, null, 0.0).daysSinceWettingRain())
    }

    @Test
    fun `a gap before the last wetting rain does not affect the count`() {
        assertEquals(1, window(null, 20.0, 0.0).daysSinceWettingRain())
    }

    @Test
    fun `the threshold is what decides, and it can be moved`() {
        val w = window(3.0, 0.0)
        assertNull(w.daysSinceWettingRain(thresholdMillimetres = 5.0))
        assertEquals(1, w.daysSinceWettingRain(thresholdMillimetres = 2.0))
    }
}
