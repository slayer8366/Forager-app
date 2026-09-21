package com.zynergy.forager.domain.port

import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.RainfallWindow
import com.zynergy.forager.domain.Species
import java.time.LocalDate

/**
 * Soil, terrain, and the lag between a wetting rain and fruiting.
 *
 * This interface exists so the gap is visible in the type system rather than filled in with a
 * plausible number. Every method returns [Outcome], and the only implementation this app ships
 * answers [Outcome.Unsupported], because:
 *
 * - no soil or terrain dataset is wired into this app;
 * - a fruiting lag is a fitted quantity. It depends on a weather time series, a species, and a
 *   model trained and validated against real fruiting records. None of those exist here.
 *
 * A number invented for these would look exactly as authoritative as a measured one, and someone
 * would drive somewhere on it. "Unsupported" is the honest answer until there is a model, and the
 * UI renders it as such rather than as an empty chart.
 */
interface TerrainSource {

    /** Dominant soil description for an area. */
    suspend fun soil(area: BoundingBox): Outcome<String>

    /** Elevation range and slope character for an area. */
    suspend fun terrain(area: BoundingBox): Outcome<String>

    /**
     * Expected days between a wetting rain and fruiting, for a species in an area around a date.
     * A fitted quantity, not a lookup.
     */
    suspend fun fruitingLagDays(
        species: Species,
        area: BoundingBox,
        onOrAbout: LocalDate,
    ): Outcome<ClosedRange<Int>>
}


/**
 * Recent rainfall for an area.
 *
 * Separate from [TerrainSource] because it is a different kind of thing: this one has a real
 * implementation that returns measured values, where soil, terrain and lag do not. Keeping them
 * apart stops "weather works" being read as "conditions work".
 */
interface WeatherSource {

    /**
     * Daily rainfall for the [days] most recent days, ending today.
     *
     * Implementations report days the source could not supply as missing rather than as zero, and
     * return [Outcome.Partial] when any are missing.
     */
    suspend fun recentRainfall(area: BoundingBox, days: Int): Outcome<RainfallWindow>
}
