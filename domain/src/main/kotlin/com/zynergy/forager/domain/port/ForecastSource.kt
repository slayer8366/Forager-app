package com.zynergy.forager.domain.port

import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.Species
import java.time.LocalDate

/**
 * One weather cell's sighting chance for a forager group in one week.
 *
 * The shape follows the forecast project's R1 (`forager-forecast/docs/planning/SPEC.md:61-62`):
 * every scored cell carries a chance, an uncertainty, and an in-or-out applicability flag. The
 * field set is confirmed by the artifact contract (F0 in
 * `docs/research/2026-09-20-forecast-integration-and-map-layering.md`) before any implementation
 * fills it; until then the only source in this app answers Unsupported and no cell is ever built.
 *
 * [chance] is the chance the group is *reported* in this cell this week, given anyone reported
 * fungi there that week. It is not the chance mushrooms are present, and not the chance of a
 * find. That reference class is stated wherever the number is shown.
 */
data class CellSightingChance(
    val cell: BoundingBox,
    val chance: Double,
    val uncertainty: ClosedRange<Double>,
    val applicable: Boolean,
) {
    init {
        require(chance in 0.0..1.0) { "chance is a fraction between 0 and 1" }
        require(uncertainty.start in 0.0..1.0 && uncertainty.endInclusive in 0.0..1.0) {
            "uncertainty bounds are fractions between 0 and 1"
        }
    }
}

/** The forecast's cells inside an area, for one group and one week. */
data class AreaSightingChance(
    val group: Species,
    val week: LocalDate,
    val weatherThrough: LocalDate,
    val cells: List<CellSightingChance>,
)

/**
 * The fruiting forecast, as published by the forager-forecast project.
 *
 * Separate from [TerrainSource.fruitingLagDays] because the two are different quantities: that one
 * asks for days between a wetting rain and fruiting, this one asks for a weekly sighting chance.
 * Deriving one from the other would be a made-up number. The only implementation this app ships
 * answers [Outcome.Unsupported], because no forecast has been published for any area yet; the
 * forecast project publishes an ecoregion only after its model beats a calendar baseline on
 * held-out years, and none has.
 *
 * An implementation answers [Outcome.Partial] when some cells in the area are masked, and
 * [Outcome.Unsupported] when the area or the group has no published forecast, which is different
 * from a fetch that failed.
 */
interface ForecastSource {

    /** Sighting chance for [group] in the cells covering [area], for the week containing [on]. */
    suspend fun sightingChance(group: Species, area: BoundingBox, on: LocalDate): Outcome<AreaSightingChance>
}
