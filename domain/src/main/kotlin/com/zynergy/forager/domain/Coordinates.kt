package com.zynergy.forager.domain

/**
 * A point on the earth, validated at construction so no later stage has to re-check it.
 *
 * Latitude and longitude are checked rather than clamped: a value outside range is a bug in the
 * caller, and silently folding 200 degrees into 160 would hide it.
 */
data class Coordinates(val latitude: Double, val longitude: Double) {

    init {
        require(latitude in MIN_LATITUDE..MAX_LATITUDE) {
            "latitude $latitude is outside $MIN_LATITUDE..$MAX_LATITUDE"
        }
        require(longitude in MIN_LONGITUDE..MAX_LONGITUDE) {
            "longitude $longitude is outside $MIN_LONGITUDE..$MAX_LONGITUDE"
        }
        require(!latitude.isNaN() && !longitude.isNaN()) { "coordinates cannot be NaN" }
    }

    companion object {
        const val MIN_LATITUDE = -90.0
        const val MAX_LATITUDE = 90.0
        const val MIN_LONGITUDE = -180.0
        const val MAX_LONGITUDE = 180.0
    }
}

/**
 * A rectangular search area, used by the map and the trip planner.
 *
 * Deliberately does not handle the antimeridian: a box whose west edge exceeds its east edge is
 * rejected rather than interpreted, because guessing which of the two readings the caller meant is
 * how a search silently covers the wrong half of the world.
 */
data class BoundingBox(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    init {
        require(south < north) { "south $south must be below north $north" }
        require(west < east) { "west $west must be west of east $east (antimeridian unsupported)" }
        // Validate the corners through Coordinates so the range rules live in one place.
        Coordinates(south, west)
        Coordinates(north, east)
    }

    fun contains(point: Coordinates): Boolean =
        point.latitude in south..north && point.longitude in west..east
}
