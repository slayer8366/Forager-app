package com.zynergy.forager.data.inaturalist

import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.Species
import com.zynergy.forager.domain.TaxonRank
import com.zynergy.forager.domain.port.SpeciesCatalog
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * [SpeciesCatalog] backed by the iNaturalist v1 API.
 *
 * Three things this class is careful about.
 *
 * A truncated page is reported as [Outcome.Partial], not [Outcome.Ok]. The API tells us
 * `total_results`; when it exceeds what came back, the caller is told so rather than being handed a
 * short list that reads as the complete answer.
 *
 * A taxon whose rank this app does not model becomes [TaxonRank.UNKNOWN] rather than being guessed
 * into the nearest familiar rank, and a taxon missing an id or a scientific name is dropped rather
 * than synthesised. Dropping is itself reported: if any row is unusable the result is Partial.
 *
 * [MAX_PER_PAGE] is this app's operating limit. The API permits 200 per page; that the range exists
 * does not make it safe to use on a phone in the field, so the ceiling is stated here and applied to
 * whatever is asked for.
 */
class INaturalistCatalog(
    private val http: HttpTransport,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : SpeciesCatalog {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: String, limit: Int): Outcome<List<Species>> {
        val perPage = limit.coerceIn(1, MAX_PER_PAGE)
        val url = "$baseUrl/taxa?q=${encode(query)}&per_page=$perPage"
        return fetch(url) { body ->
            val envelope = json.decodeFromString<TaxaEnvelope>(body)
            envelope.results to envelope.totalResults
        }
    }

    override suspend fun recordedIn(area: BoundingBox, limit: Int): Outcome<List<Species>> {
        val perPage = limit.coerceIn(1, MAX_PER_PAGE)
        val url = buildString {
            append("$baseUrl/observations/species_counts")
            append("?swlat=${area.south}&swlng=${area.west}")
            append("&nelat=${area.north}&nelng=${area.east}")
            append("&per_page=$perPage")
        }
        return fetch(url) { body ->
            val envelope = json.decodeFromString<SpeciesCountsEnvelope>(body)
            envelope.results.mapNotNull { it.taxon } to envelope.totalResults
        }
    }

    /**
     * Shared request, parse and map path. Kept in one place so the failure and truncation rules
     * cannot drift between the two endpoints.
     */
    private suspend fun fetch(
        url: String,
        parse: (String) -> Pair<List<TaxonDto>, Int>,
    ): Outcome<List<Species>> {
        val response = try {
            http.get(url)
        } catch (e: Exception) {
            return Outcome.Failed("could not reach iNaturalist", e)
        }
        if (!response.isSuccess) {
            return Outcome.Failed("iNaturalist returned HTTP ${response.status}")
        }
        val (rows, totalResults) = try {
            parse(response.body)
        } catch (e: Exception) {
            return Outcome.Failed("could not read the iNaturalist response", e)
        }

        val species = rows.mapNotNull { it.toSpeciesOrNull() }
        val dropped = rows.size - species.size
        val truncated = totalResults > rows.size

        return when {
            dropped > 0 && truncated -> Outcome.Partial(
                species,
                "$dropped of ${rows.size} rows were unusable, and $totalResults taxa match in total",
            )
            dropped > 0 -> Outcome.Partial(species, "$dropped of ${rows.size} rows were unusable")
            truncated -> Outcome.Partial(species, "$totalResults taxa match in total")
            else -> Outcome.Ok(species)
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    companion object {
        const val DEFAULT_BASE_URL = "https://api.inaturalist.org/v1"

        /** This app's ceiling. The API allows 200. */
        const val MAX_PER_PAGE = 50
    }
}

/** Maps a wire taxon to the domain, or null when it lacks what the domain requires. */
internal fun TaxonDto.toSpeciesOrNull(): Species? {
    val identifier = id?.toString() ?: return null
    val scientific = name?.takeIf { it.isNotBlank() } ?: return null
    return Species(
        catalogId = identifier,
        scientificName = scientific,
        commonName = preferredCommonName?.takeIf { it.isNotBlank() },
        rank = rank.toTaxonRank(),
    )
}

/** Unrecognised ranks become [TaxonRank.UNKNOWN] rather than being guessed into a neighbour. */
internal fun String?.toTaxonRank(): TaxonRank = when (this?.lowercase()) {
    "kingdom" -> TaxonRank.KINGDOM
    "phylum" -> TaxonRank.PHYLUM
    "class" -> TaxonRank.CLASS
    "order" -> TaxonRank.ORDER
    "family" -> TaxonRank.FAMILY
    "genus" -> TaxonRank.GENUS
    "species" -> TaxonRank.SPECIES
    "subspecies", "variety", "form" -> TaxonRank.SUBSPECIES
    else -> TaxonRank.UNKNOWN
}
