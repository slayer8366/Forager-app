package com.zynergy.forager.domain.usecase

import com.zynergy.forager.domain.Fix
import com.zynergy.forager.domain.Identification
import com.zynergy.forager.domain.IdentificationChange
import com.zynergy.forager.domain.JournalEntry
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.port.Clock
import com.zynergy.forager.domain.port.IdSource
import com.zynergy.forager.domain.port.JournalStore
import com.zynergy.forager.domain.sameAs

/**
 * Writes one journal entry.
 *
 * An entry that is unidentified and has no notes is refused as a [Outcome.Failed] rather than by
 * throwing, because this is reachable from a user tapping save on an empty form, which is not an
 * exceptional condition. A missing location is not an error: entries without a fix are kept and
 * simply do not appear on the map.
 */
class RecordSighting(
    private val store: JournalStore,
    private val clock: Clock,
    private val ids: IdSource,
) {
    suspend operator fun invoke(
        identification: Identification,
        notes: String = "",
        where: Fix? = null,
        photoCount: Int = 0,
    ): Outcome<JournalEntry> {
        if (identification == Identification.Unidentified && notes.isBlank()) {
            return Outcome.Failed("nothing to record: add a name or a note")
        }
        if (photoCount < 0) {
            return Outcome.Failed("photoCount cannot be negative")
        }
        val entry = JournalEntry.first(
            id = ids.newId(),
            recordedAt = clock.now(),
            identification = identification,
            notes = notes,
            where = where,
            photoCount = photoCount,
        )
        return store.save(entry)
    }
}

/**
 * Changes what an entry is identified as, keeping what it was identified as before.
 *
 * The earlier identifications are never edited or removed: a change appends. Choosing what the entry
 * is already identified as is refused, since it would add a line to the history that says nothing.
 * Moving back to [Identification.Unidentified] is allowed; "I no longer think it is that" is a real
 * change of mind, and the history still shows what it was thought to be.
 */
class ReidentifyEntry(
    private val store: JournalStore,
    private val clock: Clock,
) {
    suspend operator fun invoke(entry: JournalEntry, identification: Identification): Outcome<JournalEntry> {
        if (entry.identification.sameAs(identification)) {
            return Outcome.Failed("the entry is already identified as that")
        }
        return store.addIdentification(entry.id, IdentificationChange(identification, clock.now()))
    }
}
