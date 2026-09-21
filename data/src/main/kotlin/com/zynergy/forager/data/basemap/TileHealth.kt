package com.zynergy.forager.data.basemap

/**
 * What the last basemap tile request says about whether the map can be trusted to be drawn.
 *
 * MapLibre reports nothing to the app when individual tiles fail. A blocked or offline basemap just
 * renders as an empty grid, which looks like a place with no roads rather than a map that did not
 * load. OpenStreetMap's policy says access "may be blocked without prior notice", so the app watches
 * the responses itself and turns them into something a person can act on.
 */
sealed interface TileHealth {
    data object Unknown : TileHealth
    data object Healthy : TileHealth
    data class Problem(val message: String) : TileHealth
}

/** Classifies one tile response. [statusCode] is null when no response arrived at all. */
fun tileHealthFor(statusCode: Int?): TileHealth = when {
    statusCode == null ->
        TileHealth.Problem("Map tiles are not loading: no connection to the map server.")
    statusCode in 200..299 || statusCode == 304 -> TileHealth.Healthy
    statusCode == 403 || statusCode == 418 || statusCode == 429 ->
        TileHealth.Problem(
            "Map tiles are being refused by OpenStreetMap (HTTP $statusCode). " +
                "Your journal and plans still work; the background map does not.",
        )
    statusCode == 404 -> TileHealth.Healthy
    else -> TileHealth.Problem("Map tiles are not loading: the map server answered HTTP $statusCode.")
}
