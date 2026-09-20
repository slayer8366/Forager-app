package com.zynergy.forager.domain

import java.time.LocalDate

/**
 * A planned outing: a day, an area to cover, and what is being looked for.
 *
 * Targets are held as [Species] rather than bare identifiers so a plan stays readable offline,
 * which is the condition it is actually used in.
 */
data class TripPlan(
    val id: String,
    val name: String,
    val date: LocalDate,
    val area: BoundingBox,
    val targets: List<Species>,
) {
    init {
        require(id.isNotBlank()) { "id cannot be blank" }
        require(name.isNotBlank()) { "a plan needs a name" }
        require(targets.distinctBy { it.catalogId }.size == targets.size) {
            "the same target appears twice"
        }
    }

    /** Whether a journal entry falls inside this plan's area, for reviewing a trip afterwards. */
    fun covers(entry: JournalEntry): Boolean =
        entry.where?.let { area.contains(it) } == true
}
