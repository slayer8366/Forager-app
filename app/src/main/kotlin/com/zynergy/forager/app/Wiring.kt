package com.zynergy.forager.app

import com.zynergy.forager.data.inaturalist.HttpResponse
import com.zynergy.forager.data.inaturalist.HttpTransport
import android.content.Context
import com.zynergy.forager.data.inaturalist.INaturalistCatalog
import com.zynergy.forager.data.inaturalist.UnavailableTerrainSource
import com.zynergy.forager.data.openmeteo.OpenMeteoWeatherSource
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.port.Clock
import com.zynergy.forager.domain.port.LocationSource
import com.zynergy.forager.domain.port.IdSource
import com.zynergy.forager.domain.usecase.AssessPlanTiming
import com.zynergy.forager.domain.usecase.PlanTrip
import com.zynergy.forager.domain.usecase.RecordSighting
import com.zynergy.forager.domain.usecase.SearchSpecies
import com.zynergy.forager.domain.usecase.SuggestTargets
import com.zynergy.forager.domain.usecase.UpcomingPlans
import com.zynergy.forager.presentation.SpeciesSearchPresenter
import com.zynergy.forager.persistence.ForagerStores
import com.zynergy.forager.presentation.ConditionsPresenter
import com.zynergy.forager.presentation.PlanTimingPresenter
import com.zynergy.forager.presentation.SeasonalityPresenter
import com.zynergy.forager.presentation.TripPlannerPresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * HTTP over the platform's own client, behind the transport interface the data module owns.
 *
 * Timeouts are stated rather than left to the platform default, which can be effectively unbounded
 * on a phone with a weak signal. A failure here becomes [Outcome.Failed] upstream, never a silent
 * empty result.
 */
class AndroidHttpTransport : HttpTransport {

    override suspend fun get(url: String): HttpResponse = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            HttpResponse(status, body)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
        const val USER_AGENT = "Forager/0.1 (Android)"
    }
}

class SystemClock : Clock {
    override fun now(): Instant = Instant.now()
    override fun today(): LocalDate = LocalDate.now()
}

class UuidIdSource : IdSource {
    override fun newId(): String = UUID.randomUUID().toString()
}

/** One place the graph is assembled, so no screen constructs its own dependencies. */
class AppContainer(context: Context) {
    private val stores = ForagerStores.open(context)
    val location: LocationSource = AndroidLocationSource(context)
    private val catalog = INaturalistCatalog(AndroidHttpTransport())
    private val terrain = UnavailableTerrainSource()
    private val weather = OpenMeteoWeatherSource(AndroidHttpTransport())
    private val clock = SystemClock()
    private val ids = UuidIdSource()

    val journalStore = stores.journal
    private val planStore = stores.plans

    val today: LocalDate get() = clock.today()

    val recordSighting = RecordSighting(journalStore, clock, ids)
    val searchPresenter = SpeciesSearchPresenter(SearchSpecies(catalog))
    val plannerPresenter = TripPlannerPresenter(
        SuggestTargets(catalog),
        PlanTrip(planStore, clock, ids),
        UpcomingPlans(planStore, clock),
    )
    val timingPresenter = PlanTimingPresenter(AssessPlanTiming(catalog))
    val seasonalityPresenter = SeasonalityPresenter(catalog)
    val conditionsPresenter = ConditionsPresenter(terrain, weather)
}
