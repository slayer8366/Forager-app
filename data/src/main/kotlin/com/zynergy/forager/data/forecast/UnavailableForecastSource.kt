package com.zynergy.forager.data.forecast

import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.Species
import com.zynergy.forager.domain.port.AreaSightingChance
import com.zynergy.forager.domain.port.ForecastSource
import java.time.LocalDate

/**
 * The only [ForecastSource] this app ships, and it answers "unsupported" to every area.
 *
 * True today: the forecast project has published nothing. It stays true until an ecoregion passes
 * that project's skill gate and its artifacts are on the worker, at which point a source that reads
 * them replaces this class. Until then the screen names the gap, which is better than a hidden
 * feature: hidden reads as absent, a named gap reads as honest.
 */
class UnavailableForecastSource : ForecastSource {

    override suspend fun sightingChance(
        group: Species,
        area: BoundingBox,
        on: LocalDate,
    ): Outcome<AreaSightingChance> = Outcome.Unsupported(
        "a sighting-chance forecast (none is published yet for any area)",
    )
}
