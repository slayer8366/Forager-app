package com.zynergy.forager.domain

/** The taxonomic ranks this app distinguishes. Anything finer is recorded but not modelled. */
enum class TaxonRank { KINGDOM, PHYLUM, CLASS, ORDER, FAMILY, GENUS, SPECIES, SUBSPECIES, UNKNOWN }

/**
 * A taxon as this app understands it.
 *
 * [catalogId] is the identifier from whichever catalog supplied it, kept so a record can be traced
 * back to its source. [commonName] is nullable because many taxa have none, and inventing one
 * would be a fabricated value.
 */
data class Species(
    val catalogId: String,
    val scientificName: String,
    val commonName: String?,
    val rank: TaxonRank,
) {
    init {
        require(catalogId.isNotBlank()) { "catalogId cannot be blank" }
        require(scientificName.isNotBlank()) { "scientificName cannot be blank" }
    }

    /** What to show in a list: the common name when there is one, else the scientific name. */
    val displayName: String get() = commonName?.takeIf { it.isNotBlank() } ?: scientificName
}
