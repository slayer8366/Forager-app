package com.zynergy.forager.persistence

import com.zynergy.forager.domain.JournalEntry
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.TripPlan
import com.zynergy.forager.domain.port.JournalStore
import com.zynergy.forager.domain.port.TripPlanStore
import java.time.LocalDate

/**
 * Journal storage backed by Room.
 *
 * A database failure comes back as [Outcome.Failed] carrying the cause rather than as an empty
 * list, so "your journal is empty" and "your journal could not be read" stay different answers on
 * screen. That distinction is the reason the Outcome type exists.
 */
class RoomJournalStore(private val dao: JournalDao) : JournalStore {

    override suspend fun save(entry: JournalEntry): Outcome<JournalEntry> = try {
        dao.insert(entry.toRow())
        Outcome.Ok(entry)
    } catch (e: Exception) {
        Outcome.Failed("could not save the journal entry", e)
    }

    override suspend fun all(): Outcome<List<JournalEntry>> = try {
        Outcome.Ok(dao.all().map { it.toEntry() })
    } catch (e: Exception) {
        Outcome.Failed("could not read the journal", e)
    }
}

class RoomTripPlanStore(private val dao: TripPlanDao) : TripPlanStore {

    override suspend fun save(plan: TripPlan): Outcome<TripPlan> = try {
        dao.save(plan.toRow(), plan.toTargetRows())
        Outcome.Ok(plan)
    } catch (e: Exception) {
        Outcome.Failed("could not save the trip plan", e)
    }

    override suspend fun upcoming(from: LocalDate): Outcome<List<TripPlan>> = try {
        val plans = dao.upcoming(from.toEpochDay())
        val targetsByPlan = dao.targetsFor(plans.map { it.id }).groupBy { it.planId }
        Outcome.Ok(
            plans.map { row ->
                row.toPlan(targetsByPlan[row.id].orEmpty().sortedBy { it.position }.map { it.toSpecies() })
            },
        )
    } catch (e: Exception) {
        Outcome.Failed("could not read saved trip plans", e)
    }
}
