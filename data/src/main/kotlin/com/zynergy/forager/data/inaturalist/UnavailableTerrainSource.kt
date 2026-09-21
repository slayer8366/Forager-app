package com.zynergy.forager.data.inaturalist

import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.Species
import com.zynergy.forager.domain.port.TerrainSource
import java.time.LocalDate

/**
 * The only [TerrainSource] this app ships, and it answers "unsupported" to everything.
 *
 * This class is not a placeholder to be quietly filled in with estimates. It exists so that soil,
 * terrain and fruiting-lag questions have a real answer in the app today, and that answer is that
 * nothing here can answer them:
 *
 * - No soil or terrain dataset is wired in. Returning "loamy, well drained" for any polygon on
 *   earth would be a sentence with no source behind it.
 * - A fruiting lag is the number of days between a wetting rain and fruiting. Producing it needs a
 *   weather time series for the area, and a model relating that series to fruiting records, fitted
 *   and validated. This app has neither. A plausible "7 to 14 days" would be indistinguishable, on
 *   screen, from a measured one, and someone would plan a drive around it.
 *
 * When a real source arrives it replaces this class. Until then the screen says the question cannot
 * be answered here, which is true and useful, rather than showing a chart of invented numbers.
 */
class UnavailableTerrainSource : TerrainSource {

    override suspend fun soil(area: BoundingBox): Outcome<String> =
        Outcome.Unsupported("soil data (no soil dataset is wired into this app)")

    override suspend fun terrain(area: BoundingBox): Outcome<String> =
        Outcome.Unsupported("terrain data (no elevation dataset is wired into this app)")

    override suspend fun fruitingLagDays(
        species: Species,
        area: BoundingBox,
        onOrAbout: LocalDate,
    ): Outcome<ClosedRange<Int>> = Outcome.Unsupported(
        "rain-to-fruiting lag (needs a weather series and a model fitted to fruiting records)",
    )
}
