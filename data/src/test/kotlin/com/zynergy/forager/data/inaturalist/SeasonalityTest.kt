package com.zynergy.forager.data.inaturalist

import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.Seasonality
import com.zynergy.forager.domain.Species
import com.zynergy.forager.domain.TaxonRank
import com.zynergy.forager.domain.port.TerrainSource
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val AREA = BoundingBox(south = 47.0, west = -123.0, north = 47.9, east = -122.1)
private val CHANTERELLE = Species("47347", "Cantharellus cibarius", "Golden Chanterelle", TaxonRank.SPECIES)

private class Canned(private val body: String, private val status: Int = 200) : HttpTransport {
    var lastUrl: String? = null
        private set
    override suspend fun get(url: String): HttpResponse {
        lastUrl = url
        return HttpResponse(status, body)
    }
}

private fun fixture(name: String): String =
    requireNotNull(object {}.javaClass.getResource("/$name")) { "missing $name" }.readText()

class SeasonalityRequestTest {

    @Test
    fun `the histogram is asked for by taxon, box, observed date and month buckets`() = runTest {
        val http = Canned("""{"results":{"month_of_year":{}}}""")
        INaturalistCatalog(http, "https://example.test/v1").seasonality(CHANTERELLE, AREA)

        val url = http.lastUrl!!
        assertTrue(url.contains("taxon_id=47347"), url)
        assertTrue(url.contains("interval=month_of_year"), url)
        assertTrue(url.contains("date_field=observed"), url)
        assertTrue(url.contains("swlat=47.0"), url)
    }
}

class SeasonalityMappingTest {

    @Test
    fun `a real histogram maps to twelve months with the recorded peak`() = runTest {
        val catalog = INaturalistCatalog(Canned(fixture("histogram-chanterelle-puget.json")))

        val ok = assertIs<Outcome.Ok<Seasonality>>(catalog.seasonality(CHANTERELLE, AREA))

        assertEquals(12, ok.value.countsByMonth.size)
        assertEquals(Month.OCTOBER, ok.value.busiestMonth)
        assertEquals(226, ok.value.countsByMonth[Month.OCTOBER])
        assertEquals(0, ok.value.countsByMonth[Month.APRIL], "April really is zero in this data")
        assertEquals(945, ok.value.total)
    }

    @Test
    fun `months the api omits are filled with zero, not left missing`() = runTest {
        val catalog = INaturalistCatalog(Canned("""{"results":{"month_of_year":{"10":5}}}"""))

        val ok = assertIs<Outcome.Ok<Seasonality>>(catalog.seasonality(CHANTERELLE, AREA))

        assertEquals(12, ok.value.countsByMonth.size)
        assertEquals(5, ok.value.countsByMonth[Month.OCTOBER])
        assertEquals(0, ok.value.countsByMonth[Month.JANUARY])
    }

    @Test
    fun `a bucket key that is not a month is skipped and reported rather than ignored`() = runTest {
        val catalog = INaturalistCatalog(Canned("""{"results":{"month_of_year":{"10":5,"weird":3}}}"""))

        val partial = assertIs<Outcome.Partial<Seasonality>>(catalog.seasonality(CHANTERELLE, AREA))
        assertTrue(partial.note.contains("not months"), partial.note)
        assertEquals(5, partial.value.countsByMonth[Month.OCTOBER])
    }

    @Test
    fun `a server error is a failure, not an empty year`() = runTest {
        val catalog = INaturalistCatalog(Canned("", status = 500))
        val failed = assertIs<Outcome.Failed>(catalog.seasonality(CHANTERELLE, AREA))
        assertTrue(failed.reason.contains("500"))
    }
}

/**
 * The terrain source ships answering "unsupported" to everything. These tests exist so that if
 * someone later fills it with estimates, the change is deliberate and visible rather than quiet.
 */
class UnavailableTerrainSourceTest {

    private val source: TerrainSource = UnavailableTerrainSource()

    @Test
    fun `soil is unsupported and says why`() = runTest {
        val out = assertIs<Outcome.Unsupported>(source.soil(AREA))
        assertTrue(out.capability.contains("soil"), out.capability)
    }

    @Test
    fun `terrain is unsupported and says why`() = runTest {
        val out = assertIs<Outcome.Unsupported>(source.terrain(AREA))
        assertTrue(out.capability.contains("terrain"), out.capability)
    }

    @Test
    fun `fruiting lag is unsupported, naming the model it would need`() = runTest {
        val out = assertIs<Outcome.Unsupported>(
            source.fruitingLagDays(CHANTERELLE, AREA, LocalDate.of(2026, 10, 12)),
        )
        assertTrue(out.capability.contains("model"), out.capability)
    }
}
