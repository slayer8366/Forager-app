package com.zynergy.forager.domain

import java.time.Instant

/**
 * What a journal entry is identified as.
 *
 * Three states, kept apart on purpose:
 * - [Taxon] is a catalog record at any rank, so "Fungi" or the genus "Russula" is a taxon picked
 *   through search at a higher rank, not a separate type.
 * - [Unconfirmed] is text the forager typed and never tied to a catalog record. It is stored exactly
 *   as typed and nothing in this app turns it into a [Taxon]; only the forager does, by choosing one.
 *   Users' strongest complaint about other apps was a name typed offline becoming the wrong species
 *   on upload.
 * - [Unidentified] is an explicit answer, not a missing one.
 *
 * An unconfirmed name cannot be a [Species], because a species needs a catalog id this text does
 * not have, and inventing one would be the fabricated value this codebase refuses.
 */
sealed interface Identification {

    /** A catalog taxon, and how the forager came to choose it. */
    data class Taxon(val species: Species, val source: TaxonSource) : Identification

    /** A name as typed, not matched to any catalog record. */
    data class Unconfirmed(val typedName: String) : Identification {
        init {
            require(typedName.isNotBlank()) { "an unconfirmed name cannot be blank; that is Unidentified" }
        }
    }

    data object Unidentified : Identification
}

/**
 * How a [Identification.Taxon] was chosen.
 *
 * [NOT_RECORDED] exists for entries written before identification history was kept (schema version
 * 2). Those entries could carry a species but never said where it came from, and naming a source for
 * them would be a guess shown as a fact.
 */
enum class TaxonSource { SEARCH, PLAN_TARGET, RECENT_ENTRY, NOT_RECORDED }

/** One identification, and when the forager settled on it. */
data class IdentificationChange(val identification: Identification, val at: Instant)

/** The species, when the identification is a catalog taxon; null for a typed name or unidentified. */
val Identification.species: Species?
    get() = (this as? Identification.Taxon)?.species

/**
 * Whether two identifications name the same thing. How the name was chosen does not matter here:
 * picking the same chanterelle from search after picking it from a plan is not a new identification.
 */
fun Identification.sameAs(other: Identification): Boolean = when (this) {
    is Identification.Taxon -> other is Identification.Taxon && other.species.catalogId == species.catalogId
    is Identification.Unconfirmed -> other is Identification.Unconfirmed && other.typedName == typedName
    Identification.Unidentified -> other == Identification.Unidentified
}
