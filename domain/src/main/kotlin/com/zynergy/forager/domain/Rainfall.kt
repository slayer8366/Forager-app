package com.zynergy.forager.domain

import java.time.LocalDate

/**
 * One day's rainfall, where [millimetres] is null when the source had no reading for that day.
 *
 * Null is modelled rather than defaulted to zero because the two mean opposite things to a forager:
 * zero is "it stayed dry", missing is "nobody knows". Summing a missing day as zero understates
 * every total it touches and nothing downstream can tell afterwards.
 */
data class DailyRainfall(val date: LocalDate, val millimetres: Double?) {
    init {
        require(millimetres == null || millimetres >= 0.0) { "rainfall cannot be negative" }
        require(millimetres == null || !millimetres.isNaN()) { "rainfall cannot be NaN" }
    }

    val isMissing: Boolean get() = millimetres == null
}

/**
 * Consecutive days of rainfall for an area, most recent last.
 *
 * Carries its own gaps. [totalMillimetres] sums only the days that have readings and
 * [missingDays] says how many did not, so a caller can decide whether a total over a window with
 * holes in it is worth showing at all.
 */
data class RainfallWindow(
    val days: List<DailyRainfall>,
    val area: BoundingBox,
) {
    init {
        require(days.isNotEmpty()) { "a rainfall window needs at least one day" }
        require(days.zipWithNext().all { (a, b) -> a.date.isBefore(b.date) }) {
            "days must be in ascending date order with no repeats"
        }
    }

    val missingDays: Int get() = days.count { it.isMissing }

    val hasGaps: Boolean get() = missingDays > 0

    /** Sum over the days that have readings. Missing days contribute nothing and are counted above. */
    val totalMillimetres: Double get() = days.mapNotNull { it.millimetres }.sum()

    val firstDate: LocalDate get() = days.first().date
    val lastDate: LocalDate get() = days.last().date

    /**
     * Days since the most recent day whose rainfall reached [thresholdMillimetres], counting back
     * from the last day in the window.
     *
     * Null when no day in the window reached it, which is different from zero and must not be shown
     * as "today". Null is also returned when a missing day sits between the last wetting rain and
     * now, because the answer would then depend on a day nobody has a reading for.
     */
    fun daysSinceWettingRain(thresholdMillimetres: Double = DEFAULT_WETTING_MM): Int? {
        require(thresholdMillimetres > 0.0) { "threshold must be positive" }
        val lastIndex = days.indexOfLast { (it.millimetres ?: 0.0) >= thresholdMillimetres }
        if (lastIndex < 0) return null
        val gapAfter = days.drop(lastIndex + 1).any { it.isMissing }
        if (gapAfter) return null
        return days.lastIndex - lastIndex
    }

    companion object {
        /**
         * What counts as a wetting rain, in millimetres over one day.
         *
         * A judgement, stated here so it can be argued with rather than buried in a screen. Light
         * drizzle wets the surface and runs off; this threshold is meant to mark rain that reaches
         * the litter layer. It is not derived from this app's own data, because this app has none.
         */
        const val DEFAULT_WETTING_MM = 5.0
    }
}
