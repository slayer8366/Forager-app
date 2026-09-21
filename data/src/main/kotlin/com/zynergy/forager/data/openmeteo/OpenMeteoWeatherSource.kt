package com.zynergy.forager.data.openmeteo

import com.zynergy.forager.data.inaturalist.HttpTransport
import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.DailyRainfall
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.RainfallWindow
import com.zynergy.forager.domain.port.WeatherSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeParseException

@Serializable
internal data class ForecastEnvelope(
    val elevation: Double? = null,
    val daily: DailyBlock = DailyBlock(),
)

@Serializable
internal data class DailyBlock(
    val time: List<String> = emptyList(),
    @SerialName("precipitation_sum") val precipitationSum: List<Double?> = emptyList(),
)

/**
 * Recent rainfall from Open-Meteo's forecast endpoint, which carries recent past days as well as
 * future ones.
 *
 * **Why the forecast endpoint and not the archive.** The reanalysis archive is the better product
 * for history, but it runs about five days behind: probing it on 2026-09-20 returned real values up
 * to the 15th and nulls for the five days after. Those five days are exactly the ones a forager is
 * asking about. Mixing the two would cover the window at the cost of a seam between two products in
 * the middle of one series, and the two disagree: the same coordinate reported grid elevation 86 m
 * from the archive and 93 m here, so they are not the same cell. One product with no seam is the
 * cheaper thing to keep honest for a two-week window. The archive is the right choice if this app
 * ever needs months of history, and that is a different source, not a widened parameter here.
 *
 * **Pinned request parameters.** `elevation=nan` disables Open-Meteo's own downscaling to a 90 m
 * digital elevation model, so two points inside one grid cell get the same weather instead of two
 * different answers; `cell_selection=nearest` stops a nearby land cell being substituted for the
 * one asked for. Both were verified live rather than taken from documentation. Together they make a
 * cell's weather the cell's own value, which is what an area query means.
 *
 * A day the source has no value for stays missing rather than becoming zero, and any missing day
 * makes the whole window [Outcome.Partial].
 */
class OpenMeteoWeatherSource(
    private val http: HttpTransport,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : WeatherSource {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun recentRainfall(area: BoundingBox, days: Int): Outcome<RainfallWindow> {
        val requested = days.coerceIn(1, MAX_PAST_DAYS)
        val centre = area.centreOfArea()
        val url = buildString {
            append("$baseUrl/forecast")
            append("?latitude=${"%.4f".format(centre.first)}")
            append("&longitude=${"%.4f".format(centre.second)}")
            append("&daily=precipitation_sum")
            append("&past_days=$requested&forecast_days=1")
            append("&timezone=UTC&elevation=nan&cell_selection=nearest")
        }

        val response = try {
            http.get(url)
        } catch (e: Exception) {
            return Outcome.Failed("could not reach the weather service", e)
        }
        if (!response.isSuccess) {
            return Outcome.Failed("the weather service returned HTTP ${response.status}")
        }

        val block = try {
            json.decodeFromString<ForecastEnvelope>(response.body).daily
        } catch (e: Exception) {
            return Outcome.Failed("could not read the weather response", e)
        }

        if (block.time.isEmpty()) {
            return Outcome.Failed("the weather service returned no days")
        }
        if (block.time.size != block.precipitationSum.size) {
            return Outcome.Failed(
                "the weather service returned ${block.time.size} dates and " +
                    "${block.precipitationSum.size} rainfall values",
            )
        }

        val parsed = mutableListOf<DailyRainfall>()
        var unparseableDates = 0
        block.time.forEachIndexed { index, text ->
            val date = try {
                LocalDate.parse(text)
            } catch (_: DateTimeParseException) {
                unparseableDates++
                return@forEachIndexed
            }
            parsed += DailyRainfall(date, block.precipitationSum[index])
        }
        if (parsed.isEmpty()) return Outcome.Failed("no day in the weather response had a usable date")

        val window = RainfallWindow(parsed.sortedBy { it.date }, area)
        val notes = buildList {
            if (window.hasGaps) add("${window.missingDays} of ${window.days.size} days had no reading")
            if (unparseableDates > 0) add("$unparseableDates dates could not be read and were dropped")
        }

        return if (notes.isEmpty()) Outcome.Ok(window) else Outcome.Partial(window, notes.joinToString("; "))
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.open-meteo.com/v1"

        /** This app's ceiling. The endpoint allows more; a forager is asking about a fortnight. */
        const val MAX_PAST_DAYS = 31
    }
}

/** Centre of the area as latitude to longitude, which is the point a cell query needs. */
internal fun BoundingBox.centreOfArea(): Pair<Double, Double> =
    (south + north) / 2 to (west + east) / 2
