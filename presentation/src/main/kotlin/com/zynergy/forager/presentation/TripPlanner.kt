package com.zynergy.forager.presentation

import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.Species
import com.zynergy.forager.domain.TripPlan
import com.zynergy.forager.domain.usecase.PlanTrip
import com.zynergy.forager.domain.usecase.SuggestTargets
import java.time.LocalDate

data class TripPlannerUiState(
    val suggestions: List<Species> = emptyList(),
    val saved: TripPlan? = null,
    val notice: Notice? = null,
)

/** The map area to plan in, and what to look for there. Same mapping discipline as search. */
class TripPlannerPresenter(
    private val suggestTargets: SuggestTargets,
    private val planTrip: PlanTrip,
) {
    suspend fun suggest(area: BoundingBox): TripPlannerUiState =
        when (val outcome = suggestTargets(area)) {
            is Outcome.Ok -> TripPlannerUiState(suggestions = outcome.value)
            is Outcome.Partial -> TripPlannerUiState(outcome.value, notice = Notice.Incomplete(outcome.note))
            is Outcome.Failed -> TripPlannerUiState(notice = Notice.Problem(outcome.reason))
            is Outcome.Unsupported -> TripPlannerUiState(notice = Notice.NotAvailable(outcome.capability))
        }

    suspend fun save(
        name: String,
        date: LocalDate,
        area: BoundingBox,
        targets: List<Species>,
    ): TripPlannerUiState = when (val outcome = planTrip(name, date, area, targets)) {
        is Outcome.Ok -> TripPlannerUiState(saved = outcome.value)
        is Outcome.Partial -> TripPlannerUiState(saved = outcome.value, notice = Notice.Incomplete(outcome.note))
        is Outcome.Failed -> TripPlannerUiState(notice = Notice.Problem(outcome.reason))
        is Outcome.Unsupported -> TripPlannerUiState(notice = Notice.NotAvailable(outcome.capability))
    }
}
