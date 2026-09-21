package com.zynergy.forager.presentation

import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Coordinates
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val VIEW = BoundingBox(south = 46.0, west = -124.0, north = 49.0, east = -121.0)
private const val W = 600f
private const val H = 400f

private fun projection() = EquirectangularProjection(VIEW, W, H)

private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.01f) =
    assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual")

class MapProjectionTest {

    @Test
    fun `a canvas with no area is refused rather than dividing by zero`() {
        assertFailsWith<IllegalArgumentException> { EquirectangularProjection(VIEW, 0f, H) }
        assertFailsWith<IllegalArgumentException> { EquirectangularProjection(VIEW, W, 0f) }
    }

    @Test
    fun `the north west corner is the top left of the canvas`() {
        val p = projection().toScreen(Coordinates(VIEW.north, VIEW.west))
        assertClose(0f, p.x)
        assertClose(0f, p.y)
    }

    @Test
    fun `the south east corner is the bottom right of the canvas`() {
        val p = projection().toScreen(Coordinates(VIEW.south, VIEW.east))
        assertClose(W, p.x)
        assertClose(H, p.y)
    }

    @Test
    fun `north is up, which is the sign error worth having a test for`() {
        val north = projection().toScreen(Coordinates(48.0, -122.5))
        val south = projection().toScreen(Coordinates(47.0, -122.5))
        assertTrue(north.y < south.y, "a more northerly point must sit higher on the canvas")
    }

    @Test
    fun `the centre of the view is the centre of the canvas`() {
        val p = projection().toScreen(Coordinates(47.5, -122.5))
        assertClose(W / 2, p.x)
        assertClose(H / 2, p.y)
    }

    @Test
    fun `a tap in the middle of the canvas gives the centre of the view`() {
        val c = projection().toCoordinates(W / 2, H / 2)
        assertEquals(47.5, c.latitude, 1e-9)
        assertEquals(-122.5, c.longitude, 1e-9)
    }

    @Test
    fun `screen to world to screen returns where it started`() {
        val points = listOf(
            ScreenPoint(0f, 0f),
            ScreenPoint(W, H),
            ScreenPoint(123f, 45f),
            ScreenPoint(W / 3, H * 0.8f),
        )
        val projection = projection()
        points.forEach { start ->
            val there = projection.toCoordinates(start.x, start.y)
            val back = projection.toScreen(there)
            assertClose(start.x, back.x)
            assertClose(start.y, back.y)
        }
    }

    @Test
    fun `world to screen to world returns where it started`() {
        val places = listOf(
            Coordinates(46.0, -124.0),
            Coordinates(49.0, -121.0),
            Coordinates(47.6062, -122.3321),
        )
        val projection = projection()
        places.forEach { start ->
            val onScreen = projection.toScreen(start)
            val back = projection.toCoordinates(onScreen.x, onScreen.y)
            assertEquals(start.latitude, back.latitude, 1e-4)
            assertEquals(start.longitude, back.longitude, 1e-4)
        }
    }

    @Test
    fun `a point outside the view lands outside the canvas rather than being pinned to the edge`() {
        val wellNorth = projection().toScreen(Coordinates(52.0, -122.5))
        assertTrue(wellNorth.y < 0f, "clamping would put a distant marker on the border, looking local")

        val wellEast = projection().toScreen(Coordinates(47.5, -119.0))
        assertTrue(wellEast.x > W)
    }

    @Test
    fun `visibility follows the view, not the canvas`() {
        val projection = projection()
        assertTrue(projection.isVisible(Coordinates(47.5, -122.5)))
        assertFalse(projection.isVisible(Coordinates(52.0, -122.5)))
    }

    @Test
    fun `an area projects to the rectangle covering it`() {
        val area = BoundingBox(south = 47.0, west = -123.0, north = 48.0, east = -122.0)
        val (topLeft, bottomRight) = projection().rectFor(area)

        assertTrue(topLeft.x < bottomRight.x, "left edge must be left of the right edge")
        assertTrue(topLeft.y < bottomRight.y, "top edge must be above the bottom edge")
        // The area is the middle third of the view in each direction.
        assertClose(W / 3, topLeft.x)
        assertClose(2 * W / 3, bottomRight.x)
    }

    @Test
    fun `a degree of latitude of ground spans the canvas height divided by the view's span`() {
        // The view is 3 degrees tall on a 400 px canvas, so one degree is 133.3 px.
        val radii = projection().radiiFor(111_320.0, Coordinates(47.5, -122.5))
        assertClose(H / 3, radii.y, tolerance = 0.5f)
    }

    @Test
    fun `a ground circle is wider than tall here, because longitude degrees shrink with latitude`() {
        val at = Coordinates(47.5, -122.5)
        val radii = projection().radiiFor(500.0, at)

        // Degrees are the same pixel size both ways in this view (600/3 and 400/3 are not, so
        // normalise): the ratio of pixel radii should be 1/cos(latitude) times the pixel aspect.
        val pixelAspect = (W / 3) / (H / 3)
        val expected = pixelAspect / kotlin.math.cos(Math.toRadians(47.5)).toFloat()
        assertClose(expected, radii.x / radii.y, tolerance = 0.01f)
        assertTrue(radii.x > radii.y)
    }
}
