package com.zynergy.forager.domain.offline

import com.zynergy.forager.domain.BoundingBox

/** What saving an area for offline use would cost, and whether it is allowed. */
sealed interface OfflineDownloadPlan {

    /** The whole area at full detail fits in what is left of the allowance. */
    data class FullDetail(val maxZoom: Int, val tiles: Long) : OfflineDownloadPlan

    /** Full detail does not fit. The area has to shrink or saved regions go; detail is never cut. */
    data class TooLarge(val tilesNeeded: Long, val remaining: Long) : OfflineDownloadPlan

    /** The offline server has no tiles here at all. */
    data object OutsideCoverage : OfflineDownloadPlan
}

/**
 * Plans a download against the app's tile allowance.
 *
 * The allowance is 6000 tiles in total across every saved region, as the owner set it. There are
 * no accounts, so it is enforced per installation: a reinstall resets it. Enforcing it per person
 * would need the offline server to know who is asking, and it does not.
 *
 * Every download is full detail, to [MAX_ZOOM], which is where the offline extract itself stops.
 * The owner ruled on 2026-09-20 to keep zoom at 15 rather than save coarser maps when the allowance
 * runs short, so an area that does not fit is refused with the count it needs. An earlier version
 * offered the most detail that fit instead; that fallback is gone.
 */
class PlanOfflineDownload(
    private val coverage: BoundingBox,
    private val allowance: Long = ALLOWANCE_TILES,
) {
    operator fun invoke(area: BoundingBox, tilesAlreadyUsed: Long): OfflineDownloadPlan {
        if (!intersects(area, coverage)) return OfflineDownloadPlan.OutsideCoverage
        val remaining = (allowance - tilesAlreadyUsed).coerceAtLeast(0)

        val full = TileMath.tilesBetween(area, MIN_ZOOM, MAX_ZOOM)
        return if (full <= remaining) {
            OfflineDownloadPlan.FullDetail(MAX_ZOOM, full)
        } else {
            OfflineDownloadPlan.TooLarge(full, remaining)
        }
    }

    /** True when part of [area] falls outside what the offline server has. */
    fun partlyOutsideCoverage(area: BoundingBox): Boolean =
        area.south < coverage.south || area.north > coverage.north ||
            area.west < coverage.west || area.east > coverage.east

    private fun intersects(a: BoundingBox, b: BoundingBox): Boolean =
        a.west < b.east && a.east > b.west && a.south < b.north && a.north > b.south

    companion object {
        const val ALLOWANCE_TILES = 6000L
        const val MIN_ZOOM = 0
        const val MAX_ZOOM = 15
    }
}
