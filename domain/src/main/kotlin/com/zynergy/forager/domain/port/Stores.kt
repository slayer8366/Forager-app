package com.zynergy.forager.domain.port

import com.zynergy.forager.domain.JournalEntry
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.TripPlan
import java.time.Instant
import java.time.LocalDate

interface JournalStore {
    suspend fun save(entry: JournalEntry): Outcome<JournalEntry>
    suspend fun all(): Outcome<List<JournalEntry>>
}

interface TripPlanStore {
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
