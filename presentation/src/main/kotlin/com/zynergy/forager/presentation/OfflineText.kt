package com.zynergy.forager.presentation

import com.zynergy.forager.domain.offline.OfflineDownloadPlan
import com.zynergy.forager.domain.offline.PlanOfflineDownload

private fun tiles(n: Long) = "%,d".format(n)

/** How much of the offline allowance is spoken for. */
fun allowanceText(used: Long, allowance: Long = PlanOfflineDownload.ALLOWANCE_TILES): String =
    "${tiles(used.coerceAtMost(allowance))} of ${tiles(allowance)} offline tiles used"

/** What saving the current area would do, in words, before anything is downloaded. */
fun offlinePlanText(plan: OfflineDownloadPlan): String = when (plan) {
    is OfflineDownloadPlan.FullDetail ->
        "Saves this area at full detail: ${tiles(plan.tiles)} tiles."
    is OfflineDownloadPlan.TooLarge ->
        "Too large to save. This area needs ${tiles(plan.tilesNeeded)} tiles at full detail and " +
            "${tiles(plan.remaining)} are left. Make the area smaller or delete a saved region."
    OfflineDownloadPlan.OutsideCoverage ->
        "Offline maps are not available here. The offline map server covers the continental US only."
}
