package com.zynergy.forager.presentation

import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.Species
import com.zynergy.forager.domain.usecase.SearchSpecies

data class SpeciesSearchUiState(
    val query: String = "",
    val results: List<Species> = emptyList(),
    val notice: Notice? = null,
    val isSearching: Boolean = false,
) {
    /** True only when a search completed, succeeded, and genuinely matched nothing. */
    val isEmptyResult: Boolean
        get() = !isSearching && results.isEmpty() && notice == null && query.isNotBlank()
}

/**
 * Species search state, as a plain suspend function of a query.
 *
 * No scope, no Android type, no framework: the Android ViewModel owns the coroutine scope and calls
 * this. That keeps every mapping rule below testable without a Looper or an Activity.
 */
class SpeciesSearchPresenter(private val searchSpecies: SearchSpecies) {

    suspend fun search(query: String): SpeciesSearchUiState {
        if (query.isBlank()) return SpeciesSearchUiState()
        return when (val outcome = searchSpecies(query)) {
            is Outcome.Ok -> SpeciesSearchUiState(query, outcome.value)
            is Outcome.Partial -> SpeciesSearchUiState(
                query = query,
                results = outcome.value,
                notice = Notice.Incomplete(outcome.note),
            )
            is Outcome.Failed -> SpeciesSearchUiState(query, notice = Notice.Problem(outcome.reason))
            is Outcome.Unsupported -> SpeciesSearchUiState(
                query = query,
                notice = Notice.NotAvailable(outcome.capability),
            )
        }
    }
}
