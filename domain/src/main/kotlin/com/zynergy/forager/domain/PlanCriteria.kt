package com.zynergy.forager.domain

import java.time.LocalDate
import java.time.Month

/**
 * What a forager is planning around: where, when, and what they hope to find.
 *
 * Held separately from [TripPlan] because a draft is legal in states a saved plan is not: no name
 * yet, no targets yet, an area still being dragged around the map. Validation belongs at save time,
 * not while someone is still choosing.
 */
data class PlanCriteria(
    val area: BoundingBox,
    val date: LocalDate,
    val targets: List<Species> = emptyList(),
    val name: String = "",
) {
    val month: Month get() = date.month

    fun withTarget(species: Species): PlanCriteria =
        if (targets.any { it.catalogId == species.catalogId }) this
        else copy(targets = targets + species)

    fun withoutTarget(species: Species): PlanCriteria =
        copy(targets = targets.filterNot { it.catalogId == species.catalogId })

    companion object {
        /** A saved plan as a draft, for editing it. */
        fun of(plan: TripPlan) = PlanCriteria(plan.area, plan.date, plan.targets, plan.name)
    }
}

/**
 * How well one target matches the chosen month, judged only on where its records fall.
 *
 * Deliberately a share of records and a rank, not a probability and not a score out of ten. The
 * underlying data supports "a fifth of records here are in October"; it does not support "you have
 * a 20% chance", and naming it that way would be the fabrication this codebase keeps refusing.
 */
data class TargetTiming(
    val species: Species,
    val chosenMonth: Month,
    val shareInChosenMonth: Double,
    val busiestMonth: Month?,
    val totalRecords: Int,
) {
    /** True when the chosen month is the one carrying the most records. */
    val isPeakMonth: Boolean get() = busiestMonth == chosenMonth

    /** No records at all in this area: nothing can be said, which is not the same as "a bad month". */
    val hasNoRecords: Boolean get() = totalRecords == 0

    companion object {
        fun from(seasonality: Seasonality, chosen: Month): TargetTiming = TargetTiming(
            species = seasonality.species,
            chosenMonth = chosen,
            shareInChosenMonth = seasonality.shareIn(chosen),
            busiestMonth = seasonality.busiestMonth,
            totalRecords = seasonality.total,
        )
    }
}
