package com.zynergy.forager.domain.usecase

import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.Species
import com.zynergy.forager.domain.port.SpeciesCatalog

/**
 * Species search, with the two rules that belong here rather than in the catalog implementation.
 *
 * A one-character query is refused without asking the source at all: it matches most of a catalog,
 * so the round trip costs the user time and tells them nothing.
 *
 * [MAX_RESULTS] is this app's operating limit, not the source's. A catalog may well serve 200 rows
 * per page; a forager scrolling a phone in the field does not want them, so the ceiling is stated
 * here and applied to whatever the caller asks for.
 */
class SearchSpecies(private val catalog: SpeciesCatalog) {

    suspend operator fun invoke(query: String, limit: Int = DEFAULT_RESULTS): Outcome<List<Species>> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) {
            return Outcome.Failed("a search needs at least $MIN_QUERY_LENGTH characters")
        }
        val effectiveLimit = limit.coerceIn(1, MAX_RESULTS)
        return catalog.search(trimmed, effectiveLimit)
    }

    companion object {
        const val MIN_QUERY_LENGTH = 2
        const val DEFAULT_RESULTS = 20
        const val MAX_RESULTS = 50
    }
}
