package com.zynergy.forager.domain.offline

import com.zynergy.forager.domain.BoundingBox
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.floor
import kotlin.math.tan

/**
 * Web Mercator tile arithmetic, the scheme every tile source this app uses is cut to.
 *
 * Counting is exact rather than estimated from area, because the allowance is a hard number
 * and an estimate that runs low lets a download overshoot it.
 */
object TileMath {

    /** Web Mercator cannot represent the poles. Tile sources stop here. */
    const val MAX_LATITUDE = 85.05112878

    fun tileX(longitude: Double, zoom: Int): Int {
        val n = 1 shl zoom
        return floor((longitude + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
    }

    fun tileY(latitude: Double, zoom: Int): Int {
        val n = 1 shl zoom
        val lat = Math.toRadians(latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE))
        return floor((1.0 - asinh(tan(lat)) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
    }

    /** Tiles covering [area] at one zoom level. */
    fun tilesAt(area: BoundingBox, zoom: Int): Long {
        require(zoom in 0..22) { "zoom $zoom is outside what any tile source serves" }
        val columns = tileX(area.east, zoom) - tileX(area.west, zoom) + 1
        // North is the smaller y: tile rows count downward from the top of the world.
        val rows = tileY(area.south, zoom) - tileY(area.north, zoom) + 1
        return columns.toLong() * rows.toLong()
    }

    /** Tiles covering [area] at every zoom from [minZoom] to [maxZoom], inclusive. */
    fun tilesBetween(area: BoundingBox, minZoom: Int, maxZoom: Int): Long {
        require(minZoom <= maxZoom) { "minZoom $minZoom is above maxZoom $maxZoom" }
        return (minZoom..maxZoom).sumOf { tilesAt(area, it) }
    }
}
