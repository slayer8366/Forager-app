package com.zynergy.forager.persistence

import com.zynergy.forager.domain.IdentificationChange
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
        dao.save(entry.toRow(), entry.toChangeRows())
        Outcome.Ok(entry)
    } catch (e: Exception) {
        Outcome.Failed("could not save the journal entry", e)
    }

    /**
     * Every entry with its identification history.
     *
     * An entry whose history cannot be read, or is missing, is left out and counted, and the answer
     * is then [Outcome.Partial] saying how many. One damaged row should not hide the whole journal,
     * and it should not be shown as something it is not, such as an unidentified find.
     */
    override suspend fun all(): Outcome<List<JournalEntry>> = try {
        val changes = dao.allChanges().groupBy { it.entryId }
        var unreadable = 0
        val entries = dao.all().mapNotNull { row ->
            val history = changes[row.id].orEmpty().map { it.toChange() }
            val entry = if (history.isEmpty() || history.any { it == null }) {
                null
            } else {
                runCatching { row.toEntry(history.filterNotNull()) }.getOrNull()
            }
            if (entry == null) unreadable++
            entry
        }
        if (unreadable == 0) {
            Outcome.Ok(entries)
        } else {
            Outcome.Partial(
                entries,
                "$unreadable journal ${if (unreadable == 1) "entry" else "entries"} could not be read and " +
                    "${if (unreadable == 1) "is" else "are"} not shown",
            )
        }
    } catch (e: Exception) {
        Outcome.Failed("could not read the journal", e)
    }

    override suspend fun addIdentification(entryId: String, change: IdentificationChange): Outcome<JournalEntry> = try {
        if (!dao.appendChange(change.toRow(entryId))) {
            Outcome.Failed("that journal entry no longer exists")
        } else {
            val row = dao.entry(entryId)
            val history = dao.changesFor(entryId).map { it.toChange() }
            if (row == null || history.any { it == null }) {
                Outcome.Failed("the identification was saved, but the entry could not be read back")
            } else {
                Outcome.Ok(row.toEntry(history.filterNotNull()))
            }
        }
    } catch (e: Exception) {
        Outcome.Failed("could not save the identification", e)
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
