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
) : SpeciesCatalog {
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
}

class FakeTripPlanStore : TripPlanStore {
    val saved = mutableListOf<TripPlan>()
    override suspend fun save(plan: TripPlan): Outcome<TripPlan> {
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

fun species(id: String, scientific: String, common: String? = null) =
    Species(id, scientific, common, TaxonRank.SPECIES)

val CHANTERELLE = species("47348", "Cantharellus cibarius", "Golden Chanterelle")
val MOREL = species("48701", "Morchella esculenta", "Common Morel")
val PUGET_SOUND = BoundingBox(south = 47.0, west = -123.0, north = 47.9, east = -122.1)
