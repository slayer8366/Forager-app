package com.zynergy.forager.presentation

import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.Species
import com.zynergy.forager.domain.TripPlan
import com.zynergy.forager.domain.usecase.PlanTrip
import com.zynergy.forager.domain.usecase.SuggestTargets
import com.zynergy.forager.domain.usecase.UpcomingPlans
import java.time.LocalDate

data class TripPlannerUiState(
    val suggestions: List<Species> = emptyList(),
    val saved: TripPlan? = null,
    val notice: Notice? = null,
)

/** The map area to plan in, and what to look for there. Same mapping discipline as search. */
/** Saved plans still ahead, or why they could not be read. Empty with no notice means none saved. */
data class SavedPlansUiState(
    val plans: List<TripPlan> = emptyList(),
    val notice: Notice? = null,
)

class TripPlannerPresenter(
    private val suggestTargets: SuggestTargets,
    private val planTrip: PlanTrip,
    private val upcomingPlans: UpcomingPlans,
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
    ): TripPlannerUiState = toUiState(planTrip(name, date, area, targets))

    /** Saves changes to the plan with [id], keeping its id, under the same rules as a new plan. */
    suspend fun saveChanges(
        id: String,
        name: String,
        date: LocalDate,
        area: BoundingBox,
        targets: List<Species>,
    ): TripPlannerUiState = toUiState(planTrip.update(id, name, date, area, targets))

    /** Saves a copy of [plan] under a new id. */
    suspend fun duplicate(plan: TripPlan): TripPlannerUiState = toUiState(planTrip.duplicate(plan))

    private fun toUiState(outcome: Outcome<TripPlan>): TripPlannerUiState = when (outcome) {
        is Outcome.Ok -> TripPlannerUiState(saved = outcome.value)
        is Outcome.Partial -> TripPlannerUiState(saved = outcome.value, notice = Notice.Incomplete(outcome.note))
        is Outcome.Failed -> TripPlannerUiState(notice = Notice.Problem(outcome.reason))
        is Outcome.Unsupported -> TripPlannerUiState(notice = Notice.NotAvailable(outcome.capability))
    }

    /** A read failure is shown as one, so "no saved plans" and "could not read plans" never look alike. */
    suspend fun upcoming(): SavedPlansUiState = when (val outcome = upcomingPlans()) {
        is Outcome.Ok -> SavedPlansUiState(outcome.value.sortedBy { it.date })
        is Outcome.Partial -> SavedPlansUiState(outcome.value.sortedBy { it.date }, Notice.Incomplete(outcome.note))
        is Outcome.Failed -> SavedPlansUiState(notice = Notice.Problem(outcome.reason))
        is Outcome.Unsupported -> SavedPlansUiState(notice = Notice.NotAvailable(outcome.capability))
    }
}
