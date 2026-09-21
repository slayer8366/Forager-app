package com.zynergy.forager.domain

import java.time.Month

/**
 * When a taxon is actually recorded in an area, by month.
 *
 * This is an observation histogram and nothing more: it counts records people filed, so it carries
 * every bias those records have, including that more people look on weekends, near roads, and in
 * autumn. It is evidence about *reporting*, which correlates with fruiting but is not the same
 * thing, and the UI says so rather than letting a tall bar read as a forecast.
 */
data class Seasonality(
    val countsByMonth: Map<Month, Int>,
    val area: BoundingBox,
    val species: Species,
) {
    init {
        require(countsByMonth.keys.containsAll(Month.entries)) {
            "seasonality needs all twelve months, even where the count is zero"
        }
        require(countsByMonth.values.all { it >= 0 }) { "a month cannot have a negative count" }
    }

    val total: Int get() = countsByMonth.values.sum()

    val busiestMonth: Month? get() =
        if (total == 0) null else countsByMonth.maxByOrNull { it.value }?.key

    /**
     * Whether there are enough records here for the shape of the year to mean anything.
     *
     * Below this, the chart is three bars of one record each and reads, at a glance, exactly like a
     * strong seasonal signal. The bars are honest and the eye is not, so the caller is told to stop
     * drawing conclusions rather than left to notice the total in small print.
     *
     * The threshold is a judgement, not a derived statistic, and is stated here rather than buried
     * in the UI so it can be argued with and changed in one place.
     */
    val hasEnoughRecordsForPattern: Boolean get() = total >= MIN_RECORDS_FOR_PATTERN

    companion object {
        const val MIN_RECORDS_FOR_PATTERN = 20
    }

    /** Months holding the given share of all records, busiest first. Null when nothing was recorded. */
    fun monthsCovering(share: Double): List<Month>? {
        require(share > 0.0 && share <= 1.0) { "share must be in (0, 1]" }
        if (total == 0) return null
        val target = total * share
        var running = 0
        return countsByMonth.entries
            .sortedByDescending { it.value }
            .takeWhile { entry ->
                val keepGoing = running < target
                running += entry.value
                keepGoing
            }
            .map { it.key }
    }

    /** Share of records falling in [month], 0.0 when nothing was recorded at all. */
    fun shareIn(month: Month): Double =
        if (total == 0) 0.0 else (countsByMonth[month] ?: 0).toDouble() / total
}
