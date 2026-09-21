package com.zynergy.forager.domain.offline

import com.zynergy.forager.domain.BoundingBox

/** What saving an area for offline use would cost, and whether it is allowed. */
sealed interface OfflineDownloadPlan {

    /** The whole area at full detail fits in what is left of the allowance. */
    data class FullDetail(val maxZoom: Int, val tiles: Long) : OfflineDownloadPlan

    /**
     * Full detail would go over the allowance, but a coarser download fits.
     * [fullDetailTiles] is carried so the user can see what they are giving up.
     */
    data class ReducedDetail(val maxZoom: Int, val tiles: Long, val fullDetailTiles: Long) : OfflineDownloadPlan

    /** Not even the least useful detail fits. The area has to shrink or other regions go. */
    data class TooLarge(val tilesAtMinimumDetail: Long, val remaining: Long) : OfflineDownloadPlan

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
 * Detail stops at [MAX_ZOOM], which is where the offline extract itself stops; asking for more
 * would count tiles the server cannot supply. [MIN_USEFUL_ZOOM] is a judgement, not a derived
 * figure: below it roads and paths stop being drawn well enough to walk by, so a download that
 * cannot reach it is refused rather than saved as something that looks like a map and is not one.
 */
class PlanOfflineDownload(
    private val coverage: BoundingBox,
    private val allowance: Long = ALLOWANCE_TILES,
) {
    operator fun invoke(area: BoundingBox, tilesAlreadyUsed: Long): OfflineDownloadPlan {
        if (!intersects(area, coverage)) return OfflineDownloadPlan.OutsideCoverage
        val remaining = (allowance - tilesAlreadyUsed).coerceAtLeast(0)

        val full = TileMath.tilesBetween(area, MIN_ZOOM, MAX_ZOOM)
        if (full <= remaining) return OfflineDownloadPlan.FullDetail(MAX_ZOOM, full)

        for (zoom in MAX_ZOOM - 1 downTo MIN_USEFUL_ZOOM) {
            val tiles = TileMath.tilesBetween(area, MIN_ZOOM, zoom)
            if (tiles <= remaining) return OfflineDownloadPlan.ReducedDetail(zoom, tiles, full)
        }
        return OfflineDownloadPlan.TooLarge(TileMath.tilesBetween(area, MIN_ZOOM, MIN_USEFUL_ZOOM), remaining)
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
        const val MIN_USEFUL_ZOOM = 11
    }
}
