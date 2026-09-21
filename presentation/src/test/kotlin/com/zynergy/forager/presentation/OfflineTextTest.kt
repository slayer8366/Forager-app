package com.zynergy.forager.presentation

import com.zynergy.forager.domain.offline.OfflineDownloadPlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfflineTextTest {

    @Test
    fun `allowance reads with separators`() =
        assertEquals("3,477 of 6,000 offline tiles used", allowanceText(3477))

    @Test
    fun `reduced detail says what full detail would have cost and what is saved instead`() {
        val text = offlinePlanText(OfflineDownloadPlan.ReducedDetail(maxZoom = 14, tiles = 3477, fullDetailTiles = 13603))
        assertTrue(text.contains("13,603") && text.contains("zoom 14") && text.contains("3,477"), text)
    }

    @Test
    fun `too large says what is needed, what is left, and what to do`() {
        val text = offlinePlanText(OfflineDownloadPlan.TooLarge(tilesAtMinimumDetail = 900, remaining = 40))
        assertTrue(text.contains("900") && text.contains("40") && text.contains("smaller"), text)
    }

    @Test
    fun `outside coverage names the limit`() =
        assertTrue(offlinePlanText(OfflineDownloadPlan.OutsideCoverage).contains("continental US"))
}
