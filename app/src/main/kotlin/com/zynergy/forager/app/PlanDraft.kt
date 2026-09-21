package com.zynergy.forager.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Coordinates
import com.zynergy.forager.domain.PlanCriteria
import com.zynergy.forager.domain.Species
import java.time.LocalDate

/**
 * The trip being planned, shared by every tab.
 *
 * Hoisted to the app rather than owned by one screen, because the whole point is that the map
 * chooses the where, search chooses the what, and the planner judges the when. A draft held inside
 * the Plan tab would reset the moment someone went to the map to pick an area, which is exactly the
 * journey this feature exists to support.
 */
class PlanDraft(today: LocalDate) {

    var criteria by mutableStateOf(PlanCriteria(area = DEFAULT_AREA, date = today))
        private set

    /** The target whose year is charted on the map. Null until one is chosen. */
    var charted by mutableStateOf<Species?>(null)
        private set

    fun centreOn(point: Coordinates) {
        val half = criteria.area.halfSpan()
        criteria = criteria.copy(area = boxAround(point, half.first, half.second))
    }

    /** Grow or shrink the area about its own centre. [factor] above 1 widens it. */
    fun resizeBy(factor: Double) {
        val centre = criteria.area.centre()
        val (halfLat, halfLon) = criteria.area.halfSpan()
        val newLat = (halfLat * factor).coerceIn(MIN_HALF_SPAN, MAX_HALF_SPAN)
        val newLon = (halfLon * factor).coerceIn(MIN_HALF_SPAN, MAX_HALF_SPAN)
        criteria = criteria.copy(area = boxAround(centre, newLat, newLon))
    }

    fun setDate(date: LocalDate) { criteria = criteria.copy(date = date) }
    fun setName(name: String) { criteria = criteria.copy(name = name) }

    fun addTarget(species: Species) {
        criteria = criteria.withTarget(species)
        if (charted == null) charted = species
    }

    fun removeTarget(species: Species) {
        criteria = criteria.withoutTarget(species)
        if (charted?.catalogId == species.catalogId) charted = criteria.targets.firstOrNull()
    }

    fun chart(species: Species) { charted = species }

    companion object {
        val DEFAULT_AREA = BoundingBox(south = 47.0, west = -123.0, north = 47.9, east = -122.1)

        /** Clamps stop a pinch or a fat-fingered tap producing a degenerate or planet-sized box. */
        const val MIN_HALF_SPAN = 0.02
        const val MAX_HALF_SPAN = 5.0
    }
}

fun BoundingBox.centre(): Coordinates =
    Coordinates((south + north) / 2, (west + east) / 2)

fun BoundingBox.halfSpan(): Pair<Double, Double> =
    (north - south) / 2 to (east - west) / 2

/**
 * A box around a point, clamped to legal coordinates.
 *
 * Clamped rather than rejected here because this is driven by a finger on a map: dragging towards a
 * pole should stop at the pole, not throw. The domain still refuses an illegal box, so this is the
 * one place allowed to do the clamping, and it does it deliberately.
 */
fun boxAround(centre: Coordinates, halfLat: Double, halfLon: Double): BoundingBox {
    val south = (centre.latitude - halfLat).coerceIn(-89.9, 89.8)
    val north = (centre.latitude + halfLat).coerceIn(south + 0.01, 89.9)
    val west = (centre.longitude - halfLon).coerceIn(-179.9, 179.8)
    val east = (centre.longitude + halfLon).coerceIn(west + 0.01, 179.9)
    return BoundingBox(south = south, west = west, north = north, east = east)
}
