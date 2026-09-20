package com.zynergy.forager.data.inaturalist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shapes for the two iNaturalist endpoints used here.
 *
 * `ignoreUnknownKeys` is set where these are parsed: the API returns far more per taxon than this
 * app reads, and a new field appearing upstream must not break a forager's search.
 */
@Serializable
internal data class TaxaEnvelope(
    @SerialName("total_results") val totalResults: Int = 0,
    val results: List<TaxonDto> = emptyList(),
)

@Serializable
internal data class TaxonDto(
    val id: Long? = null,
    val name: String? = null,
    @SerialName("preferred_common_name") val preferredCommonName: String? = null,
    val rank: String? = null,
)

@Serializable
internal data class SpeciesCountsEnvelope(
    @SerialName("total_results") val totalResults: Int = 0,
    val results: List<SpeciesCountDto> = emptyList(),
)

@Serializable
internal data class SpeciesCountDto(
    val count: Int = 0,
    val taxon: TaxonDto? = null,
)
