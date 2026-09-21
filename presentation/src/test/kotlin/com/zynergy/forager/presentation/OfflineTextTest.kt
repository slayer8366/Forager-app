package com.zynergy.forager.presentation

import com.zynergy.forager.domain.offline.OfflineDownloadPlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineTextTest {

    @Test
    fun `allowance reads with separators`() =
        assertEquals("3,477 of 6,000 offline tiles used", allowanceText(3477))

    @Test
    fun `too large says what full detail needs, what is left, and what to do`() {
        val text = offlinePlanText(OfflineDownloadPlan.TooLarge(tilesNeeded = 13603, remaining = 4325))
        assertTrue(text.contains("13,603") && text.contains("4,325") && text.contains("smaller"), text)
        assertFalse(text.contains("zoom"), "detail is never offered as the trade-off")
    }

    @Test
    fun `outside coverage names the limit`() =
        assertTrue(offlinePlanText(OfflineDownloadPlan.OutsideCoverage).contains("continental US"))
}
