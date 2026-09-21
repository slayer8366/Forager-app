package com.zynergy.forager.data.inaturalist

import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.Species
import com.zynergy.forager.domain.TaxonRank
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Records the URL asked for and replays a canned body. No network, no sockets. */
private class FakeHttp(
    private val status: Int = 200,
    private val body: String = """{"total_results":0,"results":[]}""",
    private val throwing: Exception? = null,
) : HttpTransport {
    var lastUrl: String? = null
        private set
    var calls = 0
        private set

    override suspend fun get(url: String): HttpResponse {
        calls++
        lastUrl = url
        throwing?.let { throw it }
        return HttpResponse(status, body)
    }
}

private const val TWO_TAXA = """
{"total_results":2,"page":1,"per_page":30,"results":[
  {"id":47348,"name":"Cantharellus cibarius","preferred_common_name":"Golden Chanterelle","rank":"species","extra":"ignored"},
  {"id":48701,"name":"Morchella esculenta","preferred_common_name":"Common Morel","rank":"species"}
]}
"""

private val PUGET = BoundingBox(south = 47.0, west = -123.0, north = 47.9, east = -122.1)

class SearchRequestTest {

    @Test
    fun `the query is percent-encoded into the url`() = runTest {
        val http = FakeHttp()
        INaturalistCatalog(http, baseUrl = "https://example.test/v1").search("golden chanterelle", 10)

        assertEquals("https://example.test/v1/taxa?q=golden+chanterelle&per_page=10", http.lastUrl)
    }

    @Test
    fun `this app's per-page ceiling is applied, not the api's`() = runTest {
        val http = FakeHttp()
        INaturalistCatalog(http, "https://example.test/v1").search("morel", limit = 200)

        assertTrue(http.lastUrl!!.endsWith("per_page=${INaturalistCatalog.MAX_PER_PAGE}"))
    }

    @Test
    fun `area suggestions are scoped to fungi, not to whatever is most observed`() = runTest {
        val http = FakeHttp()
        INaturalistCatalog(http, "https://example.test/v1").recordedIn(PUGET, 10)

        assertTrue(
            http.lastUrl!!.contains("taxon_id=${INaturalistCatalog.FUNGI_TAXON_ID}"),
            "unscoped, this endpoint returns birds and trees: ${http.lastUrl}",
        )
    }

    @Test
    fun `the root taxon can be widened at the call site`() = runTest {
        val http = FakeHttp()
        INaturalistCatalog(http, "https://example.test/v1", rootTaxonId = 47126L).recordedIn(PUGET, 10)

        assertTrue(http.lastUrl!!.contains("taxon_id=47126"), http.lastUrl!!)
    }

    @Test
    fun `the bounding box becomes the four corner parameters`() = runTest {
        val http = FakeHttp()
        INaturalistCatalog(http, "https://example.test/v1").recordedIn(PUGET, 10)

        val url = http.lastUrl!!
        assertTrue(url.contains("swlat=47.0"), url)
        assertTrue(url.contains("swlng=-123.0"), url)
        assertTrue(url.contains("nelat=47.9"), url)
        assertTrue(url.contains("nelng=-122.1"), url)
    }
}

class SearchMappingTest {

    private fun catalog(body: String, status: Int = 200) =
        INaturalistCatalog(FakeHttp(status, body), "https://example.test/v1")

    @Test
    fun `a complete page maps to Ok with the taxa in order`() = runTest {
        val result = catalog(TWO_TAXA).search("mushroom", 30)

        val ok = assertIs<Outcome.Ok<List<Species>>>(result)
        assertEquals(listOf("Cantharellus cibarius", "Morchella esculenta"), ok.value.map { it.scientificName })
        assertEquals("Golden Chanterelle", ok.value.first().commonName)
        assertEquals(TaxonRank.SPECIES, ok.value.first().rank)
        assertEquals("47348", ok.value.first().catalogId)
    }

    @Test
    fun `a truncated page is Partial and says how many match in total`() = runTest {
        val body = """{"total_results":840,"results":[
          {"id":47348,"name":"Cantharellus cibarius","rank":"species"}
        ]}"""
        val result = catalog(body).search("cantharellus", 1)

        val partial = assertIs<Outcome.Partial<List<Species>>>(result)
        assertEquals(1, partial.value.size)
        assertTrue(partial.note.contains("840"), partial.note)
    }

    @Test
    fun `a row missing its scientific name is dropped and the drop is reported`() = runTest {
        val body = """{"total_results":2,"results":[
          {"id":47348,"name":"Cantharellus cibarius","rank":"species"},
          {"id":99,"rank":"species"}
        ]}"""
        val result = catalog(body).search("cantharellus", 30)

        val partial = assertIs<Outcome.Partial<List<Species>>>(result)
        assertEquals(1, partial.value.size)
        assertTrue(partial.note.contains("unusable"), partial.note)
    }

    @Test
    fun `an unmodelled rank becomes UNKNOWN rather than the nearest familiar rank`() = runTest {
        val body = """{"total_results":1,"results":[
          {"id":1,"name":"Fungi incertae sedis","rank":"complex"}
        ]}"""
        val result = catalog(body).search("fungi", 30)

        val ok = assertIs<Outcome.Ok<List<Species>>>(result)
        assertEquals(TaxonRank.UNKNOWN, ok.value.single().rank)
    }

    @Test
    fun `a blank common name is carried as absent, not as an empty string`() = runTest {
        val body = """{"total_results":1,"results":[
          {"id":1,"name":"Amanita muscaria","preferred_common_name":"  ","rank":"species"}
        ]}"""
        val result = catalog(body).search("amanita", 30)

        val ok = assertIs<Outcome.Ok<List<Species>>>(result)
        assertNull(ok.value.single().commonName)
        assertEquals("Amanita muscaria", ok.value.single().displayName)
    }

    @Test
    fun `species counts are unwrapped from their count envelope`() = runTest {
        val body = """{"total_results":1,"results":[
          {"count":37,"taxon":{"id":48701,"name":"Morchella esculenta","rank":"species"}}
        ]}"""
        val result = INaturalistCatalog(FakeHttp(200, body), "https://example.test/v1").recordedIn(PUGET, 30)

        val ok = assertIs<Outcome.Ok<List<Species>>>(result)
        assertEquals("Morchella esculenta", ok.value.single().scientificName)
    }
}

class SearchFailureTest {

    @Test
    fun `a server error names the status and is not an empty result`() = runTest {
        val result = INaturalistCatalog(FakeHttp(status = 503, body = "")).search("morel", 30)

        val failed = assertIs<Outcome.Failed>(result)
        assertTrue(failed.reason.contains("503"), failed.reason)
    }

    @Test
    fun `an unreadable body fails rather than returning nothing found`() = runTest {
        val result = INaturalistCatalog(FakeHttp(200, "not json at all")).search("morel", 30)

        val failed = assertIs<Outcome.Failed>(result)
        assertTrue(failed.reason.contains("could not read"), failed.reason)
    }

    @Test
    fun `a transport that throws becomes a Failed carrying the cause`() = runTest {
        val boom = IllegalStateException("no route to host")
        val result = INaturalistCatalog(FakeHttp(throwing = boom)).search("morel", 30)

        val failed = assertIs<Outcome.Failed>(result)
        assertEquals(boom, failed.cause)
    }

    @Test
    fun `an empty page is Ok and empty, which is different from a failure`() = runTest {
        val result = INaturalistCatalog(FakeHttp(200, """{"total_results":0,"results":[]}""")).search("zzzz", 30)

        val ok = assertIs<Outcome.Ok<List<Species>>>(result)
        assertTrue(ok.value.isEmpty())
    }
}
