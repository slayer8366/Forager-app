package com.zynergy.forager.presentation

import com.zynergy.forager.domain.Coordinates
import com.zynergy.forager.domain.Fix
import kotlin.test.Test
import kotlin.test.assertEquals

private fun fix(accuracy: Double?) = Fix(Coordinates(47.45, -122.4), accuracy)

class LocationTextTest {

    @Test
    fun `no fix says so`() = assertEquals("No location", locationLabel(null))

    @Test
    fun `a fix with no measured accuracy says so rather than quoting a number`() =
        assertEquals("Location saved without an accuracy estimate", locationLabel(fix(null)))

    @Test
    fun `a tight fix states its radius`() =
        assertEquals("Located to about 5 m", locationLabel(fix(5.0)))

    @Test
    fun `a coarse fix is labelled as rough, not as located`() =
        assertEquals("Rough location only, within about 400 m", locationLabel(fix(403.0)))

    @Test
    fun `kilometre scale reads in kilometres`() =
        assertEquals("Rough location only, within about 2.5 km", locationLabel(fix(2500.0)))

    @Test
    fun `the label changes at the same limit the map uses`() {
        assertEquals("Located to about 50 m", locationLabel(fix(Fix.USABLE_ACCURACY_METRES)))
        assertEquals(
            "Rough location only, within about 50 m",
            locationLabel(fix(Fix.USABLE_ACCURACY_METRES + 1)),
        )
    }
}
