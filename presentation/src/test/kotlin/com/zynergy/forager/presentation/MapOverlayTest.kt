package com.zynergy.forager.presentation

import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Coordinates
import com.zynergy.forager.domain.Fix
import com.zynergy.forager.domain.JournalEntry
import java.time.Instant
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun haversine(a: Coordinates, b: Coordinates): Double {
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).pow(2)
    return 2 * MapOverlayBuilder.EARTH_RADIUS_METRES * asin(sqrt(h))
}

private val WHEN = Instant.parse("2026-09-20T12:00:00Z")
private val AREA = BoundingBox(south = 47.0, west = -123.0, north = 47.9, east = -122.1)
private fun entry(id: String, fix: Fix?) = JournalEntry(id, WHEN, null, "note", fix)

class MapOverlayTest {

    @Test
    fun `every point of a ring is the stated distance from its centre`() {
        val centre = Coordinates(47.45, -122.4)
        val ring = MapOverlayBuilder.ringAround(centre, 400.0)
        ring.forEach { p ->
            assertEquals(400.0, haversine(centre, p), 0.5, "point $p")
        }
    }

    @Test
    fun `a ring spans more degrees of longitude than latitude away from the equator`() {
        val ring = MapOverlayBuilder.ringAround(Coordinates(47.45, -122.4), 1000.0)
        val lonSpan = ring.maxOf { it.longitude } - ring.minOf { it.longitude }
        val latSpan = ring.maxOf { it.latitude } - ring.minOf { it.latitude }
        // At 47.45 N a degree of longitude is cos(47.45) of a degree of latitude.
        assertEquals(1 / cos(Math.toRadians(47.45)), lonSpan / latSpan, 0.01)
    }

    @Test
    fun `a ring is closed`() {
        val ring = MapOverlayBuilder.ringAround(Coordinates(0.0, 0.0), 50.0)
        assertEquals(ring.first(), ring.last())
    }

    @Test
    fun `a ring near the antimeridian keeps its longitudes in range`() {
        val ring = MapOverlayBuilder.ringAround(Coordinates(10.0, 179.999), 5000.0)
        assertTrue(ring.all { it.longitude in -180.0..180.0 })
    }

    @Test
    fun `the planning area is a closed ring through its four corners`() {
        val overlay = MapOverlayBuilder.build(AREA, emptyList())
        assertEquals(5, overlay.planningArea.size)
        assertEquals(overlay.planningArea.first(), overlay.planningArea.last())
        assertEquals(
            setOf(47.0, 47.9),
            overlay.planningArea.map { it.latitude }.toSet(),
        )
    }

    @Test
    fun `entries without a fix are not drawn`() {
        val overlay = MapOverlayBuilder.build(AREA, listOf(entry("none", null)))
        assertTrue(overlay.markers.isEmpty())
    }

    @Test
    fun `a precise fix is a point with its ring, a coarse one is not precise`() {
        val overlay = MapOverlayBuilder.build(
            AREA,
            listOf(
                entry("tight", Fix(Coordinates(47.45, -122.4), 5.0)),
                entry("coarse", Fix(Coordinates(47.7, -122.8), 3000.0)),
            ),
        )
        val byId = overlay.markers.associateBy { it.entryId }
        assertTrue(byId.getValue("tight").precise)
        assertFalse(byId.getValue("coarse").precise)
        assertTrue(byId.getValue("coarse").accuracyRing!!.isNotEmpty())
    }

    @Test
    fun `a fix with no measured accuracy gets no ring and is not precise`() {
        val marker = MapOverlayBuilder.build(
            AREA, listOf(entry("legacy", Fix(Coordinates(47.2, -122.3), null))),
        ).markers.single()
        assertNull(marker.accuracyRing, "no radius was measured, so no area is drawn")
        assertFalse(marker.precise)
    }
}
