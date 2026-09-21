package com.zynergy.forager.presentation

import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Coordinates
import com.zynergy.forager.domain.JournalEntry
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** A journal entry as the map draws it: where, and how much of a claim that position is. */
data class EntryMarker(
    val entryId: String,
    val at: Coordinates,
    /** True only when the fix is inside the app's accuracy limit, so it may be drawn as a point. */
    val precise: Boolean,
    /** The accuracy area as a closed ring, or null when no radius was ever measured. */
    val accuracyRing: List<Coordinates>?,
)

/** Everything drawn over the basemap, in plain coordinates so it can be tested without a map. */
data class MapOverlay(
    val planningArea: List<Coordinates>,
    val markers: List<EntryMarker>,
)

/**
 * Builds what the map draws over its tiles.
 *
 * Kept free of any map SDK's types. The renderer converts this to its own feature format; the
 * decisions about what an entry claims are made here, where they can be tested headless.
 */
object MapOverlayBuilder {

    fun build(area: BoundingBox, entries: List<JournalEntry>): MapOverlay = MapOverlay(
        planningArea = listOf(
            Coordinates(area.north, area.west),
            Coordinates(area.north, area.east),
            Coordinates(area.south, area.east),
            Coordinates(area.south, area.west),
            Coordinates(area.north, area.west),
        ),
        markers = entries.mapNotNull { entry ->
            val fix = entry.where ?: return@mapNotNull null
            EntryMarker(
                entryId = entry.id,
                at = fix.coordinates,
                precise = fix.isPreciseEnoughForAFind,
                accuracyRing = fix.accuracyMetres?.let { ringAround(fix.coordinates, it) },
            )
        },
    )

    /**
     * A closed ring of points [radiusMetres] from [centre] on the ground.
     *
     * Computed as true destination points on a sphere rather than as a circle in degrees, which
     * would be squashed east to west everywhere except the equator. The renderer's own projection
     * then draws it the shape it really is. The first point is repeated at the end, since a
     * polygon ring must close.
     */
    fun ringAround(centre: Coordinates, radiusMetres: Double, points: Int = 48): List<Coordinates> {
        require(radiusMetres > 0) { "a ring needs a positive radius" }
        require(points >= 8) { "fewer than 8 points is not a usable ring" }
        val angular = radiusMetres / EARTH_RADIUS_METRES
        val lat1 = Math.toRadians(centre.latitude)
        val lon1 = Math.toRadians(centre.longitude)
        val ring = (0 until points).map { i ->
            val bearing = 2 * Math.PI * i / points
            val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing))
            val lon2 = lon1 + atan2(
                sin(bearing) * sin(angular) * cos(lat1),
                cos(angular) - sin(lat1) * sin(lat2),
            )
            Coordinates(
                Math.toDegrees(lat2).coerceIn(-90.0, 90.0),
                ((Math.toDegrees(lon2) + 540) % 360) - 180,
            )
        }
        return ring + ring.first()
    }

    /** Mean earth radius, the one GeoDistance-style calculations conventionally use. */
    const val EARTH_RADIUS_METRES = 6_371_008.8
}
