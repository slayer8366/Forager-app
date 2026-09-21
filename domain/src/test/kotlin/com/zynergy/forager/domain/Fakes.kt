package com.zynergy.forager.domain

import com.zynergy.forager.domain.port.Clock
import com.zynergy.forager.domain.port.IdSource
import com.zynergy.forager.domain.port.JournalStore
import com.zynergy.forager.domain.port.SpeciesCatalog
import com.zynergy.forager.domain.port.TripPlanStore
import java.time.Instant
import java.time.LocalDate

/**
 * Fakes that record how they were called, so a test can assert the call did not happen at all.
 * Asserting on a returned value cannot tell "refused before asking" from "asked and got nothing".
 */
class FakeCatalog(
    private val searchResult: Outcome<List<Species>> = Outcome.Ok(emptyList()),
    private val areaResult: Outcome<List<Species>> = Outcome.Ok(emptyList()),
    private val seasonalityBySpecies: Map<String, Outcome<Seasonality>> = emptyMap(),
) : SpeciesCatalog {
    var seasonalityCalls = 0
        private set

    override suspend fun seasonality(species: Species, area: BoundingBox): Outcome<Seasonality> {
        seasonalityCalls++
        return seasonalityBySpecies[species.catalogId]
            ?: Outcome.Ok(Seasonality(flatYear(0), area, species))
    }

    var searchCalls = 0
        private set
    var lastQuery: String? = null
        private set
    var lastLimit: Int? = null
        private set
    var areaCalls = 0
        private set

    override suspend fun search(query: String, limit: Int): Outcome<List<Species>> {
        searchCalls++
        lastQuery = query
        lastLimit = limit
        return searchResult
    }

    override suspend fun recordedIn(area: BoundingBox, limit: Int): Outcome<List<Species>> {
        areaCalls++
        lastLimit = limit
        return areaResult
    }
}

class FakeJournalStore : JournalStore {
    val saved = mutableListOf<JournalEntry>()
    override suspend fun save(entry: JournalEntry): Outcome<JournalEntry> {
        saved += entry
        return Outcome.Ok(entry)
    }
    override suspend fun all(): Outcome<List<JournalEntry>> = Outcome.Ok(saved.toList())

    override suspend fun addIdentification(entryId: String, change: IdentificationChange): Outcome<JournalEntry> {
        val index = saved.indexOfFirst { it.id == entryId }
        if (index < 0) return Outcome.Failed("no entry $entryId")
        val updated = saved[index].copy(identifications = saved[index].identifications + change)
        saved[index] = updated
        return Outcome.Ok(updated)
    }
}

/** Replaces by id on save, as the Room store does, so an edit is seen to replace rather than add. */
class FakeTripPlanStore : TripPlanStore {
    val saved = mutableListOf<TripPlan>()
    override suspend fun save(plan: TripPlan): Outcome<TripPlan> {
        saved.removeAll { it.id == plan.id }
        saved += plan
        return Outcome.Ok(plan)
    }
    override suspend fun upcoming(from: LocalDate): Outcome<List<TripPlan>> =
        Outcome.Ok(saved.filter { !it.date.isBefore(from) })
}

class FixedClock(private val instant: Instant, private val date: LocalDate) : Clock {
    override fun now(): Instant = instant
    override fun today(): LocalDate = date
}

class SequentialIds(private val prefix: String = "id-") : IdSource {
    private var next = 1
    override fun newId(): String = "$prefix${next++}"
}

/** An entry whose only identification is the one it was recorded with. */
fun entryOf(
    id: String,
    at: Instant,
    species: Species?,
    notes: String,
    where: Fix?,
): JournalEntry = JournalEntry.first(
    id = id,
    recordedAt = at,
    identification = species?.let { Identification.Taxon(it, TaxonSource.SEARCH) } ?: Identification.Unidentified,
    notes = notes,
    where = where,
)

fun species(id: String, scientific: String, common: String? = null) =
    Species(id, scientific, common, TaxonRank.SPECIES)

val CHANTERELLE = species("47347", "Cantharellus cibarius", "Golden Chanterelle")
val MOREL = species("48701", "Morchella esculenta", "Common Morel")
val PUGET_SOUND = BoundingBox(south = 47.0, west = -123.0, north = 47.9, east = -122.1)

/** Every month set to [count]; the twelve-month requirement is satisfied by construction. */
fun flatYear(count: Int): Map<java.time.Month, Int> =
    java.time.Month.entries.associateWith { count }

/** A year with [peak] in [peakMonth] and [rest] everywhere else. */
fun yearPeaking(peakMonth: java.time.Month, peak: Int, rest: Int = 0): Map<java.time.Month, Int> =
    java.time.Month.entries.associateWith { if (it == peakMonth) peak else rest }

/** A fix good enough to pin a find, for tests that only care that a location exists. */
fun fixAt(latitude: Double, longitude: Double, accuracyMetres: Double = 8.0): Fix =
    Fix(Coordinates(latitude, longitude), accuracyMetres)
