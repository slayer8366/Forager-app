package com.zynergy.forager.domain.usecase

import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.TripPlan
import com.zynergy.forager.domain.port.Clock
import com.zynergy.forager.domain.port.TripPlanStore

/**
 * Saved plans whose date is today or later, soonest first.
 *
 * "Today" comes from the injected clock rather than the device calendar, so a test can pin it and
 * the boundary is checked rather than assumed. A plan dated today still counts as upcoming, since
 * the trip has not necessarily happened yet.
 */
class UpcomingPlans(
    private val store: TripPlanStore,
    private val clock: Clock,
) {
    suspend operator fun invoke(): Outcome<List<TripPlan>> = store.upcoming(clock.today())
}
