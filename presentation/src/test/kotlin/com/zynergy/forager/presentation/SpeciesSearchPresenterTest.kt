package com.zynergy.forager.presentation

import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.Species
import com.zynergy.forager.domain.TaxonRank
import com.zynergy.forager.domain.port.SpeciesCatalog
import com.zynergy.forager.domain.usecase.SearchSpecies
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class StubCatalog(private val result: Outcome<List<Species>>) : SpeciesCatalog {
    var calls = 0
        private set
    override suspend fun search(query: String, limit: Int): Outcome<List<Species>> {
        calls++
        return result
    }
    override suspend fun recordedIn(area: BoundingBox, limit: Int): Outcome<List<Species>> = result
    override suspend fun seasonality(
        species: Species,
        area: BoundingBox,
    ): Outcome<com.zynergy.forager.domain.Seasonality> =
        Outcome.Unsupported("seasonality not stubbed")
}

private val CHANTERELLE = Species("47348", "Cantharellus cibarius", "Golden Chanterelle", TaxonRank.SPECIES)

private fun presenter(result: Outcome<List<Species>>, catalog: StubCatalog = StubCatalog(result)) =
    presenterWith(catalog)

private fun presenterWith(catalog: StubCatalog) = SpeciesSearchPresenter(SearchSpecies(catalog)) to catalog

class SpeciesSearchPresenterTest {

    @Test
    fun `a complete answer shows results and says nothing else`() = runTest {
        val (p, _) = presenter(Outcome.Ok(listOf(CHANTERELLE)))
        val state = p.search("chanterelle")

        assertEquals(1, state.results.size)
        assertNull(state.notice)
        assertFalse(state.isEmptyResult)
    }

    @Test
    fun `a partial answer shows the results AND says they are incomplete`() = runTest {
        val (p, _) = presenter(Outcome.Partial(listOf(CHANTERELLE), "208 taxa match in total"))
        val state = p.search("cantharellus")

        assertEquals(1, state.results.size, "partial data is still shown")
        val notice = assertIs<Notice.Incomplete>(state.notice)
        assertTrue(notice.detail.contains("208"))
    }

    @Test
    fun `a failure shows the reason and no results`() = runTest {
        val (p, _) = presenter(Outcome.Failed("iNaturalist returned HTTP 503"))
        val state = p.search("morel")

        assertTrue(state.results.isEmpty())
        val notice = assertIs<Notice.Problem>(state.notice)
        assertTrue(notice.detail.contains("503"))
    }

    @Test
    fun `an unsupported source is not rendered as no results found`() = runTest {
        val (p, _) = presenter(Outcome.Unsupported("range data"))
        val state = p.search("morel")

        assertIs<Notice.NotAvailable>(state.notice)
        assertFalse(
            state.isEmptyResult,
            "an unsupported source must not read as 'nothing lives there'",
        )
    }

    @Test
    fun `a genuine empty match is the only thing that reads as empty`() = runTest {
        val (p, _) = presenter(Outcome.Ok(emptyList()))
        val state = p.search("zzzznotataxon")

        assertTrue(state.isEmptyResult)
        assertNull(state.notice)
    }

    @Test
    fun `a blank query searches nothing and clears the screen`() = runTest {
        val catalog = StubCatalog(Outcome.Ok(listOf(CHANTERELLE)))
        val (p, _) = presenterWith(catalog)
        val state = p.search("   ")

        assertEquals(SpeciesSearchUiState(), state)
        assertEquals(0, catalog.calls, "a blank query must not reach the catalog")
    }

    @Test
    fun `the short-query refusal from the use case surfaces as a problem, not as empty`() = runTest {
        val catalog = StubCatalog(Outcome.Ok(emptyList()))
        val (p, _) = presenterWith(catalog)
        val state = p.search("c")

        assertIs<Notice.Problem>(state.notice)
        assertFalse(state.isEmptyResult)
        assertEquals(0, catalog.calls, "refused before the catalog was asked")
    }
}
