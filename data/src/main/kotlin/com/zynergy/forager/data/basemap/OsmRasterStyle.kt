package com.zynergy.forager.data.basemap

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The online basemap: OpenStreetMap's standard raster tiles, as a MapLibre style document.
 *
 * Built in code rather than fetched, so the tile URL, attribution and zoom limit are pinned in one
 * reviewed place. What OpenStreetMap's tile policy asks of an app is split across layers: the
 * attribution travels in the style, and the User-Agent is set on the HTTP client where the requests
 * are made. No offline download is ever pointed at this source, because the policy forbids it.
 */
object OsmRasterStyle {

    const val TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"

    /** OpenStreetMap's standard layer is rendered to zoom 19. Beyond that there are no tiles. */
    const val MAX_ZOOM = 19

    /** Required by the tile policy, and by the ODbL for the data under it. */
    const val ATTRIBUTION = "© OpenStreetMap contributors"

    const val SOURCE_ID = "osm"

    fun json(): String = buildJsonObject {
        put("version", 8)
        put("name", "OpenStreetMap raster")
        putJsonObject("sources") {
            putJsonObject(SOURCE_ID) {
                put("type", "raster")
                putJsonArray("tiles") { add(JsonPrimitive(TILE_URL)) }
                put("tileSize", 256)
                put("minzoom", 0)
                put("maxzoom", MAX_ZOOM)
                put("attribution", ATTRIBUTION)
            }
        }
        put(
            "layers",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("id", "osm-tiles")
                        put("type", "raster")
                        put("source", SOURCE_ID)
                    },
                )
            },
        )
    }.toString()
}

/**
 * The User-Agent every basemap request carries.
 *
 * OpenStreetMap's policy asks for a distinct, stable string naming the app, and forbids a library
 * default. The app id is included so the operators can tell this app from any other called
 * Forager. No contact email: that would send the owner's address with every tile request, and it
 * has not been agreed.
 */
fun basemapUserAgent(versionName: String, applicationId: String): String {
    require(versionName.isNotBlank() && applicationId.isNotBlank()) { "a User-Agent must name the app" }
    return "Forager/$versionName ($applicationId)"
}
