package com.zynergy.forager.persistence

import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Coordinates
import com.zynergy.forager.domain.Fix
import com.zynergy.forager.domain.Identification
import com.zynergy.forager.domain.IdentificationChange
import com.zynergy.forager.domain.JournalEntry
import com.zynergy.forager.domain.Species
import com.zynergy.forager.domain.TaxonRank
import com.zynergy.forager.domain.TaxonSource
import com.zynergy.forager.domain.TripPlan
import java.time.Instant
import java.time.LocalDate

internal fun JournalEntry.toRow() = JournalEntryRow(
    id = id,
    recordedAtEpochMillis = recordedAt.toEpochMilli(),
    notes = notes,
    latitude = where?.latitude,
    longitude = where?.longitude,
    locationAccuracyMetres = where?.accuracyMetres,
    photoCount = photoCount,
)

internal fun JournalEntry.toChangeRows(): List<IdentificationChangeRow> =
    identifications.map { it.toRow(id) }

/** The three kinds, as stored. The names are part of the schema: renaming one strands old rows. */
internal object IdentificationKind {
    const val TAXON = "TAXON"
    const val UNCONFIRMED = "UNCONFIRMED"
    const val UNIDENTIFIED = "UNIDENTIFIED"
}

internal fun IdentificationChange.toRow(entryId: String): IdentificationChangeRow {
    val at = at.toEpochMilli()
    return when (val id = identification) {
        is Identification.Taxon -> IdentificationChangeRow(
            entryId = entryId,
            changedAtEpochMillis = at,
            kind = IdentificationKind.TAXON,
            source = id.source.name,
            catalogId = id.species.catalogId,
            scientificName = id.species.scientificName,
            commonName = id.species.commonName,
            rank = id.species.rank.name,
            typedName = null,
        )
        is Identification.Unconfirmed -> IdentificationChangeRow(
            entryId = entryId,
            changedAtEpochMillis = at,
            kind = IdentificationKind.UNCONFIRMED,
            source = null,
            catalogId = null,
            scientificName = null,
            commonName = null,
            rank = null,
            typedName = id.typedName,
        )
        Identification.Unidentified -> IdentificationChangeRow(
            entryId = entryId,
            changedAtEpochMillis = at,
            kind = IdentificationKind.UNIDENTIFIED,
            source = null,
            catalogId = null,
            scientificName = null,
            commonName = null,
            rank = null,
            typedName = null,
        )
    }
}

/**
 * Rebuilds one identification, or null when the row cannot stand for one: a taxon with no catalog
 * id or name, a typed name that is blank, or a kind this version does not know. Null is reported by
 * the store as an unreadable entry; it is never turned into Unidentified, which would be a claim the
 * row does not make.
 *
 * A rank or source this version does not recognise reads as UNKNOWN or NOT_RECORDED, which is what
 * they are to this version: a value it cannot interpret.
 */
internal fun IdentificationChangeRow.toChange(): IdentificationChange? {
    val identification = when (kind) {
        IdentificationKind.TAXON -> {
            val id = catalogId?.takeIf { it.isNotBlank() } ?: return null
            val name = scientificName?.takeIf { it.isNotBlank() } ?: return null
            Identification.Taxon(
                species = Species(
                    catalogId = id,
                    scientificName = name,
                    commonName = commonName,
                    rank = rank?.let { runCatching { TaxonRank.valueOf(it) }.getOrNull() } ?: TaxonRank.UNKNOWN,
                ),
                source = source?.let { runCatching { TaxonSource.valueOf(it) }.getOrNull() }
                    ?: TaxonSource.NOT_RECORDED,
            )
        }
        IdentificationKind.UNCONFIRMED ->
            Identification.Unconfirmed(typedName?.takeIf { it.isNotBlank() } ?: return null)
        IdentificationKind.UNIDENTIFIED -> Identification.Unidentified
        else -> return null
    }
    return IdentificationChange(identification, Instant.ofEpochMilli(changedAtEpochMillis))
}

/**
 * Rebuilds an entry from its row and its identification history, oldest change first.
 *
 * A half-written location, one coordinate present and the other null, yields no location rather
 * than a point on the equator or the prime meridian. It should not be reachable, since both are
 * written together, but a row that is already wrong should not be turned into a confident marker
 * on a map.
 */
internal fun JournalEntryRow.toEntry(history: List<IdentificationChange>): JournalEntry {
    val where = if (latitude != null && longitude != null) {
        Fix(
            coordinates = Coordinates(latitude, longitude),
            // Null for version 1 rows, which never measured one. Passed through as unknown.
            accuracyMetres = locationAccuracyMetres,
        )
    } else {
        null
    }
    return JournalEntry(
        id = id,
        recordedAt = Instant.ofEpochMilli(recordedAtEpochMillis),
        identifications = history,
        notes = notes,
        where = where,
        photoCount = photoCount,
    )
}

internal fun TripPlan.toRow() = TripPlanRow(
    id = id,
    name = name,
    dateEpochDay = date.toEpochDay(),
    south = area.south,
    west = area.west,
    north = area.north,
    east = area.east,
)

internal fun TripPlan.toTargetRows(): List<TripPlanTargetRow> =
    targets.mapIndexed { index, species ->
        TripPlanTargetRow(
            planId = id,
            catalogId = species.catalogId,
            scientificName = species.scientificName,
            commonName = species.commonName,
            rank = species.rank.name,
            position = index,
        )
    }

internal fun TripPlanTargetRow.toSpecies() = Species(
    catalogId = catalogId,
    scientificName = scientificName,
    commonName = commonName,
    rank = runCatching { TaxonRank.valueOf(rank) }.getOrNull() ?: TaxonRank.UNKNOWN,
)

internal fun TripPlanRow.toPlan(targets: List<Species>) = TripPlan(
    id = id,
    name = name,
    date = LocalDate.ofEpochDay(dateEpochDay),
    area = BoundingBox(south = south, west = west, north = north, east = east),
    targets = targets,
)
