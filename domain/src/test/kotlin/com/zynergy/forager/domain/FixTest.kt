package com.zynergy.forager.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val WHEN = Instant.parse("2026-09-20T12:00:00Z")
private fun fix(accuracy: Double?) = Fix(Coordinates(47.5, -122.5), accuracy)

class FixTest {

    @Test
    fun `a radius of zero or less is not a radius`() {
        assertFailsWith<IllegalArgumentException> { fix(0.0) }
        assertFailsWith<IllegalArgumentException> { fix(-5.0) }
    }

    @Test
    fun `a fix with no measured accuracy is never treated as precise`() {
        assertFalse(fix(null).isPreciseEnoughForAFind)
    }

    @Test
    fun `a tight fix is precise enough to pin a find`() {
        assertTrue(fix(8.0).isPreciseEnoughForAFind)
    }

    @Test
    fun `the stated limit is inclusive`() {
        assertTrue(fix(Fix.USABLE_ACCURACY_METRES).isPreciseEnoughForAFind)
        assertFalse(fix(Fix.USABLE_ACCURACY_METRES + 0.1).isPreciseEnoughForAFind)
    }

    @Test
    fun `a fix covering a hillside is still recorded, just not treated as a point`() {
        val coarse = fix(2000.0)

        assertFalse(
            coarse.isPreciseEnoughForAFind,
            "the platform will hand over a 2 km radius and call it a location",
        )
        // Not rejected: it is real information, and throwing it away would be worse.
        assertTrue(coarse.accuracyMetres!! > 0)
    }
}

class EntryLocationQualityTest {

    private fun entryWith(fix: Fix?) =
        JournalEntry("e1", WHEN, null, "a note", fix)

    @Test
    fun `an entry with no fix is neither mappable nor precise`() {
        val entry = entryWith(null)
        assertFalse(entry.isMappable)
        assertFalse(entry.hasPreciseLocation)
    }

    @Test
    fun `a coarse fix is mappable but not precise, so it can be drawn as what it is`() {
        val entry = entryWith(fix(500.0))
        assertTrue(entry.isMappable, "a coarse fix should still appear on the map")
        assertFalse(entry.hasPreciseLocation, "but not as a confident point")
    }

    @Test
    fun `a tight fix is both`() {
        val entry = entryWith(fix(6.0))
        assertTrue(entry.isMappable)
        assertTrue(entry.hasPreciseLocation)
    }
}
