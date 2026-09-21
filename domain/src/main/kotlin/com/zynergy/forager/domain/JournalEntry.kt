package com.zynergy.forager.domain

import java.time.Instant

/**
 * One journal record: something seen, at a time, optionally somewhere, optionally identified.
 *
 * [identifications] is the entry's whole identification history in the order the forager made the
 * changes, and the current identification is its last item. The history is kept rather than
 * overwritten because in foraging "I first thought this was X" is safety information. It is never
 * empty: an entry saved with no name starts with an explicit [Identification.Unidentified].
 *
 * The order is the order of the changes, not of their timestamps. A phone's clock can be set back,
 * and sorting by time would then quietly put a later identification before an earlier one.
 *
 * What is *not* optional is that the entry says something. An entry that has only ever been
 * unidentified and has no notes records nothing, so it is rejected rather than stored empty.
 */
data class JournalEntry(
    val id: String,
    val recordedAt: Instant,
    val identifications: List<IdentificationChange>,
    val notes: String,
    val where: Fix?,
    val photoCount: Int = 0,
) {
    init {
        require(id.isNotBlank()) { "id cannot be blank" }
        require(identifications.isNotEmpty()) {
            "an entry always has an identification, even if it is Unidentified"
        }
        require(identifications.any { it.identification != Identification.Unidentified } || notes.isNotBlank()) {
            "an entry needs a name or notes; one with neither records nothing"
        }
        require(photoCount >= 0) { "photoCount cannot be negative" }
    }

    /** What the entry is identified as now. */
    val identification: Identification get() = identifications.last().identification

    /** The current species, when the current identification is a catalog taxon. */
    val species: Species? get() = identification.species

    /** Everything the entry was identified as before its current identification, oldest first. */
    val earlierIdentifications: List<IdentificationChange> get() = identifications.dropLast(1)

    /** True when the entry can be placed on the map at all. Entries without a fix are still valid. */
    val isMappable: Boolean get() = where != null

    /**
     * True when the fix is tight enough to stand for the find's actual location.
     *
     * Kept separate from [isMappable] so a coarse fix can still be drawn, as the circle it really
     * is, rather than either vanishing from the map or being rendered as a confident point.
     */
    val hasPreciseLocation: Boolean get() = where?.isPreciseEnoughForAFind == true

    companion object {
        /** A new entry whose history starts with [identification], made when the entry was recorded. */
        fun first(
            id: String,
            recordedAt: Instant,
            identification: Identification,
            notes: String,
            where: Fix?,
            photoCount: Int = 0,
        ) = JournalEntry(
            id = id,
            recordedAt = recordedAt,
            identifications = listOf(IdentificationChange(identification, recordedAt)),
            notes = notes,
            where = where,
            photoCount = photoCount,
        )
    }
}
