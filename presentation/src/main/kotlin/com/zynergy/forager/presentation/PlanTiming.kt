package com.zynergy.forager.presentation

import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.PlanCriteria
import com.zynergy.forager.domain.Seasonality
import com.zynergy.forager.domain.Species
import com.zynergy.forager.domain.TargetTiming
import com.zynergy.forager.domain.port.TerrainSource
import com.zynergy.forager.domain.usecase.AssessPlanTiming
import java.time.LocalDate

data class PlanTimingUiState(
    val timings: List<TargetTiming> = emptyList(),
    val notice: Notice? = null,
    val assessed: Boolean = false,
)

/** Timing for the targets in a draft plan, against the month the user chose. */
class PlanTimingPresenter(private val assess: AssessPlanTiming) {

    suspend fun assess(criteria: PlanCriteria): PlanTimingUiState =
        when (val outcome = assess.invoke(criteria)) {
            is Outcome.Ok -> PlanTimingUiState(outcome.value, assessed = true)
            is Outcome.Partial ->
                PlanTimingUiState(outcome.value, Notice.Incomplete(outcome.note), assessed = true)
            is Outcome.Failed -> PlanTimingUiState(notice = Notice.Problem(outcome.reason), assessed = true)
            is Outcome.Unsupported ->
                PlanTimingUiState(notice = Notice.NotAvailable(outcome.capability), assessed = true)
        }
}

data class SeasonalityUiState(
    val seasonality: Seasonality? = null,
    val notice: Notice? = null,
)

/** One species' year of records, for the chart on the map. */
class SeasonalityPresenter(private val catalog: com.zynergy.forager.domain.port.SpeciesCatalog) {

    suspend fun load(species: Species, criteria: PlanCriteria): SeasonalityUiState =
        when (val outcome = catalog.seasonality(species, criteria.area)) {
            is Outcome.Ok -> SeasonalityUiState(outcome.value)
            is Outcome.Partial -> SeasonalityUiState(outcome.value, Notice.Incomplete(outcome.note))
            is Outcome.Failed -> SeasonalityUiState(notice = Notice.Problem(outcome.reason))
            is Outcome.Unsupported -> SeasonalityUiState(notice = Notice.NotAvailable(outcome.capability))
        }
}

/**
 * Soil, terrain and rain-to-fruiting lag for the chosen area and date.
 *
 * Every field is a [Notice] rather than a value, because with the source this app ships every one of
 * them is [Notice.NotAvailable]. Modelling it this way means the screen shows a stated reason where
 * a number would be, instead of a blank space the user reads as "nothing to worry about".
 */
data class ConditionsUiState(
    val soil: Notice? = null,
    val terrain: Notice? = null,
    val fruitingLag: Notice? = null,
    val lagDays: ClosedRange<Int>? = null,
)

class ConditionsPresenter(private val source: TerrainSource) {

    suspend fun load(criteria: PlanCriteria, species: Species?, on: LocalDate): ConditionsUiState {
        val soil = source.soil(criteria.area).asNoticeOrNull()
        val terrain = source.terrain(criteria.area).asNoticeOrNull()
        return if (species == null) {
            ConditionsUiState(
                soil = soil,
                terrain = terrain,
                fruitingLag = Notice.NotAvailable("a target species, to say anything about lag"),
            )
        } else {
            when (val lag = source.fruitingLagDays(species, criteria.area, on)) {
                is Outcome.Ok -> ConditionsUiState(soil, terrain, null, lag.value)
                is Outcome.Partial -> ConditionsUiState(soil, terrain, Notice.Incomplete(lag.note), lag.value)
                is Outcome.Failed -> ConditionsUiState(soil, terrain, Notice.Problem(lag.reason))
                is Outcome.Unsupported ->
                    ConditionsUiState(soil, terrain, Notice.NotAvailable(lag.capability))
            }
        }
    }
}

/** Null when the outcome carried a real value; a notice otherwise. */
private fun <T> Outcome<T>.asNoticeOrNull(): Notice? = when (this) {
    is Outcome.Ok -> null
    is Outcome.Partial -> Notice.Incomplete(note)
    is Outcome.Failed -> Notice.Problem(reason)
    is Outcome.Unsupported -> Notice.NotAvailable(capability)
}
