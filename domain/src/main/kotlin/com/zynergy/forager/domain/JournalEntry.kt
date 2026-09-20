package com.zynergy.forager.domain

import java.time.Instant

/**
 * One journal record: something seen, at a time, optionally somewhere, optionally identified.
 *
 * [species] is nullable on purpose. The common case in the field is finding something you cannot
 * name yet, and an app that refuses the record until you can name it loses the observation. What is
 * *not* optional is that the entry says something: an entry with no species and no notes records
 * nothing, so it is rejected rather than stored empty.
 */
data class JournalEntry(
    val id: String,
    val recordedAt: Instant,
    val species: Species?,
    val notes: String,
    val where: Coordinates?,
    val photoCount: Int = 0,
) {
    init {
        require(id.isNotBlank()) { "id cannot be blank" }
        require(species != null || notes.isNotBlank()) {
            "an entry needs a species or notes; one with neither records nothing"
        }
        require(photoCount >= 0) { "photoCount cannot be negative" }
    }

    /** True when the entry can be placed on the map. Entries without a fix are still valid. */
    val isMappable: Boolean get() = where != null
}
