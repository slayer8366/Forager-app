package com.zynergy.forager.data.openmeteo

import com.zynergy.forager.data.inaturalist.HttpResponse
import com.zynergy.forager.data.inaturalist.HttpTransport
import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.RainfallWindow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val PUGET = BoundingBox(south = 47.0, west = -123.0, north = 47.9, east = -122.1)

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

class WeatherRequestTest {

    @Test
    fun `the query uses the centre of the area, not a corner`() = runTest {
        val http = Canned("""{"daily":{"time":["2026-09-20"],"precipitation_sum":[0.0]}}""")
        OpenMeteoWeatherSource(http, "https://example.test/v1").recentRainfall(PUGET, 14)

        val url = http.lastUrl!!
        assertTrue(url.contains("latitude=47.4500"), url)
        assertTrue(url.contains("longitude=-122.5500"), url)
    }

    @Test
    fun `downscaling and land-cell substitution are both switched off`() = runTest {
        val http = Canned("""{"daily":{"time":["2026-09-20"],"precipitation_sum":[0.0]}}""")
        OpenMeteoWeatherSource(http, "https://example.test/v1").recentRainfall(PUGET, 14)

        val url = http.lastUrl!!
        assertTrue(url.contains("elevation=nan"), "a cell must not be downscaled to a point: $url")
        assertTrue(url.contains("cell_selection=nearest"), "no land-cell substitution: $url")
    }

    @Test
    fun `this app's ceiling on past days applies`() = runTest {
        val http = Canned("""{"daily":{"time":["2026-09-20"],"precipitation_sum":[0.0]}}""")
        OpenMeteoWeatherSource(http, "https://example.test/v1").recentRainfall(PUGET, 9999)

        assertTrue(http.lastUrl!!.contains("past_days=${OpenMeteoWeatherSource.MAX_PAST_DAYS}"))
    }
}

class WeatherMappingTest {

    @Test
    fun `a real fifteen day response parses with no gaps`() = runTest {
        val source = OpenMeteoWeatherSource(Canned(fixture("openmeteo-puget-15day.json")))

        val ok = assertIs<Outcome.Ok<RainfallWindow>>(source.recentRainfall(PUGET, 14))

        assertEquals(15, ok.value.days.size)
        assertEquals(0, ok.value.missingDays)
        assertEquals("2026-09-07", ok.value.firstDate.toString())
        assertEquals("2026-09-21", ok.value.lastDate.toString())
    }

    @Test
    fun `a null day stays missing and makes the window partial`() = runTest {
        val body = """{"daily":{"time":["2026-09-18","2026-09-19","2026-09-20"],
                       "precipitation_sum":[4.0,null,1.0]}}"""
        val source = OpenMeteoWeatherSource(Canned(body))

        val partial = assertIs<Outcome.Partial<RainfallWindow>>(source.recentRainfall(PUGET, 3))

        assertEquals(1, partial.value.missingDays)
        assertEquals(5.0, partial.value.totalMillimetres, "the null day must not be summed as zero")
        assertTrue(partial.note.contains("no reading"), partial.note)
    }

    @Test
    fun `days arrive in ascending order even if the source scrambles them`() = runTest {
        val body = """{"daily":{"time":["2026-09-20","2026-09-18","2026-09-19"],
                       "precipitation_sum":[1.0,4.0,0.0]}}"""
        val ok = assertIs<Outcome.Ok<RainfallWindow>>(
            OpenMeteoWeatherSource(Canned(body)).recentRainfall(PUGET, 3),
        )
        assertEquals(
            listOf("2026-09-18", "2026-09-19", "2026-09-20"),
            ok.value.days.map { it.date.toString() },
        )
    }

    @Test
    fun `mismatched dates and values is a failure rather than a silent truncation`() = runTest {
        val body = """{"daily":{"time":["2026-09-19","2026-09-20"],"precipitation_sum":[1.0]}}"""
        val failed = assertIs<Outcome.Failed>(OpenMeteoWeatherSource(Canned(body)).recentRainfall(PUGET, 2))
        assertTrue(failed.reason.contains("2 dates"), failed.reason)
    }

    @Test
    fun `an http error is a failure, not a dry fortnight`() = runTest {
        val failed = assertIs<Outcome.Failed>(
            OpenMeteoWeatherSource(Canned("", status = 502)).recentRainfall(PUGET, 14),
        )
        assertTrue(failed.reason.contains("502"), failed.reason)
    }

    @Test
    fun `an empty day list fails rather than producing an empty window`() = runTest {
        val failed = assertIs<Outcome.Failed>(
            OpenMeteoWeatherSource(Canned("""{"daily":{"time":[],"precipitation_sum":[]}}""")).recentRainfall(PUGET, 14),
        )
        assertTrue(failed.reason.contains("no days"), failed.reason)
    }
}

class WettingRainFromRealDataTest {

    @Test
    fun `the recorded fortnight has a wetting rain eight days before the end`() = runTest {
        val ok = assertIs<Outcome.Ok<RainfallWindow>>(
            OpenMeteoWeatherSource(Canned(fixture("openmeteo-puget-15day.json"))).recentRainfall(PUGET, 14),
        )
        // 11.2 mm fell on 2026-09-13, the last day above the 5 mm threshold.
        // From 2026-09-13 to 2026-09-21 is 8 days.
        assertEquals(8, ok.value.daysSinceWettingRain())
        assertTrue(ok.value.totalMillimetres >= 11.0)
    }
}
