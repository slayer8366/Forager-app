package com.zynergy.forager.presentation

import com.zynergy.forager.domain.Fix
import kotlin.math.roundToInt

/**
 * How an entry's location is described in the journal.
 *
 * States the radius in every case, because "Located" alone reads the same for a fix good to 5 m
 * and one covering a hillside. Radii are rounded to a sensible step so the wording does not claim
 * more precision than the platform's own estimate has.
 */
fun locationLabel(fix: Fix?): String {
    val accuracy = fix?.accuracyMetres
    return when {
        fix == null -> "No location"
        accuracy == null -> "Location saved without an accuracy estimate"
        fix.isPreciseEnoughForAFind -> "Located to about ${metres(accuracy)}"
        else -> "Rough location only, within about ${metres(accuracy)}"
    }
}

private fun metres(value: Double): String = when {
    value < 10 -> "${value.roundToInt().coerceAtLeast(1)} m"
    value < 1000 -> "${(value / 10).roundToInt() * 10} m"
    else -> "%.1f km".format(value / 1000)
}
