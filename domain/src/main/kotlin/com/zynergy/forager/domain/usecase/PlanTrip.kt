package com.zynergy.forager.domain.usecase

import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.Species
import com.zynergy.forager.domain.TripPlan
import com.zynergy.forager.domain.port.Clock
import com.zynergy.forager.domain.port.IdSource
import com.zynergy.forager.domain.port.SpeciesCatalog
import com.zynergy.forager.domain.port.TripPlanStore
import java.time.LocalDate

/**
 * Creates a trip plan, and separately suggests what to look for in an area.
 *
 * A plan dated before today is refused. Recording a trip you already took is a journal entry, not a
 * plan, and letting a plan sit in the past makes "upcoming" meaningless.
 */
class PlanTrip(
    private val store: TripPlanStore,
    private val clock: Clock,
    private val ids: IdSource,
) {
    suspend operator fun invoke(
        name: String,
        date: LocalDate,
        area: BoundingBox,
        targets: List<Species>,
    ): Outcome<TripPlan> = checkAndSave(ids.newId(), name, date, area, targets)

    /**
     * Saves changes to the plan with [id], keeping the id, so the edited plan replaces the stored
     * one instead of appearing beside it.
     *
     * The same rules as a new plan apply, including the past-date refusal. A plan can only be opened
     * for editing from the upcoming list, so the one way to meet that refusal here is an edit left
     * open across midnight on the plan's own day. Refusing it keeps one rule for every saved plan
     * rather than a special case, and the refusal says what to change.
     */
    suspend fun update(
        id: String,
        name: String,
        date: LocalDate,
        area: BoundingBox,
        targets: List<Species>,
    ): Outcome<TripPlan> = checkAndSave(id, name, date, area, targets)

    /** Saves a copy of [plan] under a new id, named so the copy and the original can be told apart. */
    suspend fun duplicate(plan: TripPlan): Outcome<TripPlan> =
        checkAndSave(ids.newId(), "${plan.name} (copy)", plan.date, plan.area, plan.targets)

    private suspend fun checkAndSave(
        id: String,
        name: String,
        date: LocalDate,
        area: BoundingBox,
        targets: List<Species>,
    ): Outcome<TripPlan> {
        if (name.isBlank()) return Outcome.Failed("a plan needs a name")
        if (date.isBefore(clock.today())) {
            return Outcome.Failed("$date is in the past; record a past trip in the journal")
        }
        val duplicate = targets.groupBy { it.catalogId }.values.firstOrNull { it.size > 1 }
        if (duplicate != null) {
            return Outcome.Failed("${duplicate.first().displayName} is listed twice")
        }
        val plan = TripPlan(id, name.trim(), date, area, targets)
        return store.save(plan)
    }
}

/**
 * What has been recorded in an area, for filling a plan's target list.
 *
 * Passes the catalog's [Outcome] through untouched. A [Outcome.Partial] stays partial and an
 * [Outcome.Unsupported] stays unsupported, so the screen can say "this is some of what is here"
 * rather than presenting a short list as the whole story.
 */
class SuggestTargets(private val catalog: SpeciesCatalog) {

    suspend operator fun invoke(area: BoundingBox, limit: Int = DEFAULT_SUGGESTIONS): Outcome<List<Species>> =
        catalog.recordedIn(area, limit.coerceIn(1, MAX_SUGGESTIONS))

    companion object {
        const val DEFAULT_SUGGESTIONS = 25
        const val MAX_SUGGESTIONS = 100
    }
}
