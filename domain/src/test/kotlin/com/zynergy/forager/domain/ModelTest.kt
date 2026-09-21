package com.zynergy.forager.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoordinatesTest {

    @Test
    fun `rejects latitude beyond the pole`() {
        val error = assertFailsWith<IllegalArgumentException> { Coordinates(90.1, 0.0) }
        assertTrue(error.message!!.contains("latitude"))
    }

    @Test
    fun `rejects longitude beyond the antimeridian`() {
        assertFailsWith<IllegalArgumentException> { Coordinates(0.0, 180.1) }
    }

    @Test
    fun `rejects NaN rather than storing it`() {
        assertFailsWith<IllegalArgumentException> { Coordinates(Double.NaN, 0.0) }
    }

    @Test
    fun `accepts the poles and the antimeridian exactly`() {
        assertEquals(-90.0, Coordinates(-90.0, 180.0).latitude)
        assertEquals(180.0, Coordinates(-90.0, 180.0).longitude)
    }
}

class BoundingBoxTest {

    @Test
    fun `rejects an inverted box`() {
        assertFailsWith<IllegalArgumentException> { BoundingBox(47.9, -123.0, 47.0, -122.1) }
    }

    @Test
    fun `rejects a box that would cross the antimeridian instead of guessing`() {
        val error = assertFailsWith<IllegalArgumentException> {
            BoundingBox(south = 40.0, west = 170.0, north = 50.0, east = -170.0)
        }
        assertTrue(error.message!!.contains("antimeridian"))
    }

    @Test
    fun `contains is inclusive on the edges`() {
        assertTrue(PUGET_SOUND.contains(Coordinates(47.0, -123.0)))
        assertTrue(PUGET_SOUND.contains(Coordinates(47.5, -122.5)))
        assertFalse(PUGET_SOUND.contains(Coordinates(46.9, -122.5)))
    }
}

class SpeciesTest {

    @Test
    fun `display name prefers the common name`() {
        assertEquals("Golden Chanterelle", CHANTERELLE.displayName)
    }

    @Test
    fun `display name falls back to the scientific name rather than inventing one`() {
        val unnamed = species("1", "Cortinarius vanduzerensis")
        assertEquals("Cortinarius vanduzerensis", unnamed.displayName)
    }

    @Test
    fun `a blank common name is treated as absent`() {
        val blank = Species("1", "Amanita muscaria", "   ", TaxonRank.SPECIES)
        assertEquals("Amanita muscaria", blank.displayName)
    }
}

class JournalEntryTest {

    private val when0 = Instant.parse("2026-09-20T08:00:00Z")

    @Test
    fun `an entry with neither species nor notes records nothing and is refused`() {
        val error = assertFailsWith<IllegalArgumentException> {
            entryOf(id = "e1", at = when0, species = null, notes = "  ", where = null)
        }
        assertTrue(error.message!!.contains("records nothing"))
    }

    @Test
    fun `an unidentified find is valid when it carries notes`() {
        val entry = entryOf("e1", when0, species = null, notes = "orange, on fir", where = null)
        assertEquals("orange, on fir", entry.notes)
    }

    @Test
    fun `an entry without a fix is valid but not mappable`() {
        val entry = entryOf("e1", when0, CHANTERELLE, "", where = null)
        assertFalse(entry.isMappable)
    }

    @Test
    fun `an entry with a fix is mappable`() {
        val entry = entryOf("e1", when0, CHANTERELLE, "", fixAt(47.5, -122.5))
        assertTrue(entry.isMappable)
    }
}
