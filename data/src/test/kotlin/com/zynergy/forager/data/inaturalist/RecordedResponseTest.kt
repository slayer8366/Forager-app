package com.zynergy.forager.data.inaturalist

import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.Species
import com.zynergy.forager.domain.TaxonRank
import com.zynergy.forager.domain.BoundingBox
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The hand-written fixtures elsewhere in this module assert the rules. These two assert the *shape*,
 * because a rule tested only against a fixture I wrote myself proves the rule and not the contract.
 *
 * Both files are verbatim responses captured from api.inaturalist.org on 2026-09-20 and committed as
 * resources, so the field names this module depends on are pinned to something the API actually
 * sent rather than to the documentation.
 */
private val PUGET_SOUND_AREA = BoundingBox(south = 47.0, west = -123.0, north = 47.9, east = -122.1)

private fun fixture(name: String): String =
    requireNotNull(object {}.javaClass.getResource("/$name")) { "missing fixture $name" }
        .readText()

private class CannedHttp(private val body: String) : HttpTransport {
    override suspend fun get(url: String): HttpResponse = HttpResponse(200, body)
}

class RecordedTaxaResponseTest {

    @Test
    fun `a real taxa response parses, and its truncation is reported`() = runTest {
        val catalog = INaturalistCatalog(CannedHttp(fixture("taxa-cantharellus.json")))

        val result = catalog.search("cantharellus", 2)

        // 208 taxa match, 2 rows returned, so this must not read as the whole answer.
        val partial = assertIs<Outcome.Partial<List<Species>>>(result)
        assertEquals(2, partial.value.size)
        assertTrue(partial.note.contains("208"), partial.note)
    }

    @Test
    fun `the genus Cantharellus maps with its id, name and rank intact`() = runTest {
        val catalog = INaturalistCatalog(CannedHttp(fixture("taxa-cantharellus.json")))

        val partial = assertIs<Outcome.Partial<List<Species>>>(catalog.search("cantharellus", 2))
        val first = partial.value.first()

        assertEquals("47348", first.catalogId)
        assertEquals("Cantharellus", first.scientificName)
        assertEquals(TaxonRank.GENUS, first.rank)
        assertEquals("chanterelles", first.commonName)
    }
}

class RecordedSpeciesCountsResponseTest {

    @Test
    fun `a real species-counts response unwraps its taxa`() = runTest {
        val catalog = INaturalistCatalog(CannedHttp(fixture("species-counts-puget.json")))

        val result = catalog.recordedIn(PUGET_SOUND_AREA, 2)

        val partial = assertIs<Outcome.Partial<List<Species>>>(result)
        assertEquals("Amanita muscaria", partial.value.first().scientificName)
        assertEquals("Fly Agaric", partial.value.first().commonName)
        assertEquals(TaxonRank.SPECIES, partial.value.first().rank)
    }
}
