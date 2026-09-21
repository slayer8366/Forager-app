package com.zynergy.forager.persistence

import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Coordinates
import com.zynergy.forager.domain.JournalEntry
import com.zynergy.forager.domain.Species
import com.zynergy.forager.domain.TaxonRank
import com.zynergy.forager.domain.TripPlan
import java.time.Instant
import java.time.LocalDate

internal fun JournalEntry.toRow() = JournalEntryRow(
    id = id,
    recordedAtEpochMillis = recordedAt.toEpochMilli(),
    speciesCatalogId = species?.catalogId,
    speciesScientificName = species?.scientificName,
    speciesCommonName = species?.commonName,
    speciesRank = species?.rank?.name,
    notes = notes,
    latitude = where?.latitude,
    longitude = where?.longitude,
    photoCount = photoCount,
)

/**
 * Rebuilds an entry from its row.
 *
 * A half-written location, one coordinate present and the other null, yields no location rather
 * than a point on the equator or the prime meridian. It should not be reachable, since both are
 * written together, but a row that is already wrong should not be turned into a confident marker
 * on a map.
 */
internal fun JournalEntryRow.toEntry(): JournalEntry {
    val species = speciesCatalogId?.let { id ->
        val name = speciesScientificName ?: return@let null
        Species(
            catalogId = id,
            scientificName = name,
            commonName = speciesCommonName,
            rank = speciesRank?.let { runCatching { TaxonRank.valueOf(it) }.getOrNull() }
                ?: TaxonRank.UNKNOWN,
        )
    }
    val where = if (latitude != null && longitude != null) Coordinates(latitude, longitude) else null
    return JournalEntry(
        id = id,
        recordedAt = Instant.ofEpochMilli(recordedAtEpochMillis),
        species = species,
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
