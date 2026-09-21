package com.zynergy.forager.domain.port

import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.Seasonality
import com.zynergy.forager.domain.Species

/**
 * The boundary this app owns over a species data source. iNaturalist sits behind it.
 *
 * The domain depends on this interface and never on an HTTP client or a vendor SDK, so the search
 * and planning rules are testable with no network and the source can be replaced without touching
 * them.
 *
 * An implementation that cannot answer a given call returns [Outcome.Unsupported] naming the
 * capability. A local cache that holds no range data, for example, says so rather than returning an
 * empty list, which would read as "nothing lives there".
 */
interface SpeciesCatalog {

    suspend fun search(query: String, limit: Int): Outcome<List<Species>>

    /** Taxa recorded within [area], used by the trip planner to suggest targets. */
    suspend fun recordedIn(area: BoundingBox, limit: Int): Outcome<List<Species>>

    /**
     * Monthly record counts for [species] inside [area].
     *
     * Answers "when is this reported here", which is what the planner needs and what the data can
     * actually support. It is not a fruiting forecast; see [Seasonality].
     */
    suspend fun seasonality(species: Species, area: BoundingBox): Outcome<Seasonality>
}
