package com.zynergy.forager.domain.port

import com.zynergy.forager.domain.IdentificationChange
import com.zynergy.forager.domain.JournalEntry
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.TripPlan
import java.time.Instant
import java.time.LocalDate

interface JournalStore {
    suspend fun save(entry: JournalEntry): Outcome<JournalEntry>
    suspend fun all(): Outcome<List<JournalEntry>>

    /**
     * Appends [change] to the history of the entry with [entryId] and returns the entry as stored
     * afterwards. Earlier changes are left exactly as they were. An id with no entry is a failure,
     * not a new entry.
     */
    suspend fun addIdentification(entryId: String, change: IdentificationChange): Outcome<JournalEntry>
}

interface TripPlanStore {
    /** Saves [plan], replacing any stored plan with the same id, targets included. */
    suspend fun save(plan: TripPlan): Outcome<TripPlan>
    suspend fun upcoming(from: LocalDate): Outcome<List<TripPlan>>
}

/**
 * Time as a dependency rather than a static call, so a test can state the day instead of working
 * around whatever day it runs on.
 */
interface Clock {
    fun now(): Instant
    fun today(): LocalDate
}

/** Identifier generation, injected for the same reason as [Clock]. */
interface IdSource {
    fun newId(): String
}
