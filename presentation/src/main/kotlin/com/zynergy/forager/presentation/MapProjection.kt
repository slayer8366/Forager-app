package com.zynergy.forager.presentation

import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Coordinates
import kotlin.math.cos

/** A point on the canvas, in pixels from the top left. */
data class ScreenPoint(val x: Float, val y: Float)

/**
 * Maps between coordinates and canvas pixels for a map view.
 *
 * Pulled out of the map Composable because three things need the same answer: the planning
 * rectangle, the journal markers, and the tap that moves the area. When each carries its own copy
 * of the arithmetic they drift, and a marker landing a few hundred metres out looks like nothing at
 * all on an emulator. Out here it can be tested, including the round trip, which is the property
 * that actually catches a sign error.
 *
 * This is an equirectangular projection: latitude and longitude are stretched linearly onto the
 * canvas. Named for what it is, because it is **not** Web Mercator, and map tiles are cut to
 * Mercator. Over a box a degree or so across the difference is small, but it grows with latitude
 * and it is not a rounding error. When tiles arrive, this is the one place that changes.
 */
class EquirectangularProjection(
    private val view: BoundingBox,
    private val widthPx: Float,
    private val heightPx: Float,
) {
    init {
        require(widthPx > 0f && heightPx > 0f) { "a canvas with no area cannot be projected onto" }
    }

    private val lonSpan = view.east - view.west
    private val latSpan = view.north - view.south

    /**
     * Where a coordinate falls on the canvas.
     *
     * Deliberately not clamped to the canvas. A point outside the view returns a point outside the
     * canvas, so a caller that draws it without checking gets a marker off the edge rather than one
     * pinned to the border pretending to be inside the view.
     */
    fun toScreen(at: Coordinates): ScreenPoint = ScreenPoint(
        x = (((at.longitude - view.west) / lonSpan) * widthPx).toFloat(),
        y = ((1.0 - ((at.latitude - view.south) / latSpan)) * heightPx).toFloat(),
    )

    /** What coordinate a canvas point lands on. The inverse of [toScreen]. */
    fun toCoordinates(x: Float, y: Float): Coordinates = Coordinates(
        latitude = view.north - (y / heightPx) * latSpan,
        longitude = view.west + (x / widthPx) * lonSpan,
    )

    /** Whether a coordinate is inside the view, so a caller can skip drawing what would not show. */
    fun isVisible(at: Coordinates): Boolean = view.contains(at)

    /**
     * How many pixels a ground distance spans at a coordinate, horizontally and vertically.
     *
     * Two numbers, not one, because this projection stretches longitude: a degree of longitude
     * covers less ground the further from the equator it is, so a circle on the ground is an
     * ellipse on this canvas. Drawing an accuracy radius as a plain circle would understate the
     * uncertainty in one direction.
     */
    fun radiiFor(metres: Double, at: Coordinates): ScreenPoint {
        val metresPerDegreeLongitude = METRES_PER_DEGREE_LATITUDE * cos(Math.toRadians(at.latitude))
        return ScreenPoint(
            x = ((metres / metresPerDegreeLongitude) / lonSpan * widthPx).toFloat(),
            y = ((metres / METRES_PER_DEGREE_LATITUDE) / latSpan * heightPx).toFloat(),
        )
    }

    /** The canvas rectangle an area covers, as top-left and bottom-right. */
    fun rectFor(area: BoundingBox): Pair<ScreenPoint, ScreenPoint> =
        toScreen(Coordinates(area.north, area.west)) to toScreen(Coordinates(area.south, area.east))

    private companion object {
        /** Mean length of a degree of latitude. Varies by under 1% pole to equator. */
        const val METRES_PER_DEGREE_LATITUDE = 111_320.0
    }
}
