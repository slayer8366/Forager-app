package com.zynergy.forager.presentation

import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.Species
import com.zynergy.forager.domain.port.AreaSightingChance
import com.zynergy.forager.domain.port.ForecastSource
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * The sighting-chance line for a planning area, or why there is none.
 *
 * [line] is the number beside its words, and [referenceClass] is the sentence that says what the
 * number is a chance *of*. They are separate fields so the screen can never show one without the
 * other: lay readers misread a bare percentage mostly because nobody said what it referred to.
 */
data class SightingChanceUiState(
    val line: String? = null,
    val referenceClass: String? = null,
    val dates: String? = null,
    val notice: Notice? = null,
)

/**
 * Words the forecast's own rules forbid, anywhere a user can read them.
 *
 * The forecast project's R8 requires zero hits for "fruiting probability" in every output string.
 * This list is what the terms test searches this module's forecast copy for.
 */
val FORBIDDEN_FORECAST_TERMS = listOf("fruiting probability", "probability of finding", "chance of finding")

const val SIGHTING_CHANCE_REFERENCE_CLASS =
    "Chance this group is reported in each 11 km cell this week, where anyone is reporting fungi. " +
        "Compare areas, not spots. A high chance is not a find."

class SightingChancePresenter(private val source: ForecastSource) {

    suspend fun load(group: Species?, area: BoundingBox, on: LocalDate): SightingChanceUiState {
        if (group == null) return SightingChanceUiState(notice = Notice.NotAvailable("a target, to look up a sighting chance"))
        return when (val outcome = source.sightingChance(group, area, on)) {
            is Outcome.Ok -> outcome.value.toUiState(null)
            is Outcome.Partial -> outcome.value.toUiState(Notice.Incomplete(outcome.note))
            is Outcome.Failed -> SightingChanceUiState(notice = Notice.Problem(outcome.reason))
            is Outcome.Unsupported -> SightingChanceUiState(notice = Notice.NotAvailable(outcome.capability))
        }
    }
}

/**
 * The range across the area's applicable cells, never an average: an average would be a number
 * for a box the forecast never scored as one thing. An area with no applicable cell has no
 * forecast, and says so.
 */
private fun AreaSightingChance.toUiState(notice: Notice?): SightingChanceUiState {
    val scored = cells.filter { it.applicable }
    if (scored.isEmpty()) {
        return SightingChanceUiState(notice = Notice.NotAvailable("a forecast for this area (no cell here is scored)"))
    }
    val low = scored.minOf { it.chance }.toPercent()
    val high = scored.maxOf { it.chance }.toPercent()
    val range = if (low == high) "$low%" else "$low% to $high%"
    return SightingChanceUiState(
        line = "Sighting chance for ${group.displayName}: $range across ${scored.size} " +
            "cell${if (scored.size == 1) "" else "s"}",
        referenceClass = SIGHTING_CHANCE_REFERENCE_CLASS,
        dates = "Week of $week, weather to $weatherThrough",
        notice = notice,
    )
}

private fun Double.toPercent(): Int = (this * 100).roundToInt()
