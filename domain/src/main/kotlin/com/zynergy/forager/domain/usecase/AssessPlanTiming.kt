package com.zynergy.forager.domain.usecase

import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.PlanCriteria
import com.zynergy.forager.domain.Seasonality
import com.zynergy.forager.domain.TargetTiming
import com.zynergy.forager.domain.port.SpeciesCatalog

/**
 * Judges each target in a draft plan against the month chosen, using only where its records fall.
 *
 * Per-target failure does not sink the whole assessment. If four of five targets return and the
 * fifth fails, the caller gets the four as [Outcome.Partial] with the fifth named, because a
 * planner that shows nothing when one lookup times out is worse than one that shows four and says
 * so. A target with no records at all is still returned: "nothing has been recorded here" is an
 * answer a forager can use, and it is not the same as a bad month.
 */
class AssessPlanTiming(private val catalog: SpeciesCatalog) {

    suspend operator fun invoke(criteria: PlanCriteria): Outcome<List<TargetTiming>> {
        if (criteria.targets.isEmpty()) return Outcome.Ok(emptyList())

        val timings = mutableListOf<TargetTiming>()
        val problems = mutableListOf<String>()

        criteria.targets.forEach { species ->
            when (val outcome = catalog.seasonality(species, criteria.area)) {
                is Outcome.Ok -> timings += TargetTiming.from(outcome.value, criteria.month)
                is Outcome.Partial -> {
                    timings += TargetTiming.from(outcome.value, criteria.month)
                    problems += "${species.displayName}: ${outcome.note}"
                }
                is Outcome.Failed -> problems += "${species.displayName}: ${outcome.reason}"
                is Outcome.Unsupported ->
                    problems += "${species.displayName}: ${outcome.capability} is not available"
            }
        }

        return when {
            timings.isEmpty() && problems.isNotEmpty() ->
                Outcome.Failed("no target could be assessed: ${problems.joinToString("; ")}")
            problems.isNotEmpty() ->
                Outcome.Partial(sortedByFit(timings), problems.joinToString("; "))
            else -> Outcome.Ok(sortedByFit(timings))
        }
    }

    /** Best fit first, then by how much evidence sits behind it. */
    private fun sortedByFit(timings: List<TargetTiming>): List<TargetTiming> =
        timings.sortedWith(
            compareByDescending<TargetTiming> { it.shareInChosenMonth }
                .thenByDescending { it.totalRecords },
        )
}

/** Convenience for a screen that has one species and wants its whole year. */
class LoadSeasonality(private val catalog: SpeciesCatalog) {
    suspend operator fun invoke(criteria: PlanCriteria, index: Int): Outcome<Seasonality> {
        val species = criteria.targets.getOrNull(index)
            ?: return Outcome.Failed("no target at position $index")
        return catalog.seasonality(species, criteria.area)
    }
}
