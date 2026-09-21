package com.zynergy.forager.domain

/**
 * A measured position: where the device believed it was, and how sure it was.
 *
 * Accuracy travels with the coordinates rather than beside them because a point without its
 * uncertainty is a claim the data does not support. A phone under canopy routinely reports a
 * position that is off by more than the distance between two patches, and drawn as a bare dot it
 * looks exactly like a position good to a few metres.
 *
 * [accuracyMetres] is the radius the platform reports, conventionally at 68% confidence. The true
 * position is not guaranteed to be inside it.
 *
 * There is deliberately no timestamp here. How old a fix is matters intensely while it is being
 * captured, because the platform will hand back a cached position from an hour ago without
 * comment, and a [LocationSource] is responsible for refusing one. Once a fix has been attached to
 * an entry, the time that means anything is the entry's own, so storing a second one would add a
 * column nobody reads and a number nobody could interpret later.
 */
data class Fix(
    val coordinates: Coordinates,
    val accuracyMetres: Double,
) {
    init {
        require(accuracyMetres > 0.0) { "accuracy must be positive; $accuracyMetres is not a radius" }
        require(!accuracyMetres.isNaN()) { "accuracy cannot be NaN" }
    }

    /**
     * Whether this fix is tight enough to treat as the location of a specific find.
     *
     * The platform will hand over a fix with a 2000 metre radius and call it a location, which is
     * true and useless: that circle covers a whole hillside. A reported range describes what the
     * hardware can produce, not what is safe to build on, so the app states its own limit rather
     * than accepting whatever arrives.
     */
    val isPreciseEnoughForAFind: Boolean get() = accuracyMetres <= USABLE_ACCURACY_METRES

    val latitude: Double get() = coordinates.latitude
    val longitude: Double get() = coordinates.longitude

    companion object {
        /**
         * The app's own limit, in metres, above which a fix is recorded but not treated as pinning
         * a find. A judgement, not a derived figure: it is roughly the scale at which two patches
         * in the same wood stop being distinguishable. Stated here so it can be argued with.
         */
        const val USABLE_ACCURACY_METRES = 50.0
    }
}
