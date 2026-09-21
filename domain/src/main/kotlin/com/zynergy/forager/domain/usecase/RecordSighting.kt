package com.zynergy.forager.domain.usecase

import com.zynergy.forager.domain.Fix
import com.zynergy.forager.domain.JournalEntry
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.Species
import com.zynergy.forager.domain.port.Clock
import com.zynergy.forager.domain.port.IdSource
import com.zynergy.forager.domain.port.JournalStore

/**
 * Writes one journal entry.
 *
 * An entry with neither a species nor notes is refused as a [Outcome.Failed] rather than by throwing,
 * because this is reachable from a user tapping save on an empty form, which is not an exceptional
 * condition. A missing location is not an error: entries without a fix are kept and simply do not
 * appear on the map.
 */
class RecordSighting(
    private val store: JournalStore,
    private val clock: Clock,
    private val ids: IdSource,
) {
    suspend operator fun invoke(
        species: Species?,
        notes: String = "",
        where: Fix? = null,
        photoCount: Int = 0,
    ): Outcome<JournalEntry> {
        if (species == null && notes.isBlank()) {
            return Outcome.Failed("nothing to record: add a species or a note")
        }
        if (photoCount < 0) {
            return Outcome.Failed("photoCount cannot be negative")
        }
        val entry = JournalEntry(
            id = ids.newId(),
            recordedAt = clock.now(),
            species = species,
            notes = notes,
            where = where,
            photoCount = photoCount,
        )
        return store.save(entry)
    }
}
