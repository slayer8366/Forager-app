package com.zynergy.forager.data.basemap

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OsmRasterStyleTest {

    private val style = Json.parseToJsonElement(OsmRasterStyle.json()).jsonObject
    private val source = style.getValue("sources").jsonObject.getValue("osm").jsonObject

    @Test
    fun `it is a version 8 style, which is the only version MapLibre reads`() =
        assertEquals(8, style.getValue("version").jsonPrimitive.int)

    @Test
    fun `the source is OSM raster at 256 px, capped at OSM's own zoom limit`() {
        assertEquals("raster", source.getValue("type").jsonPrimitive.content)
        assertEquals(
            listOf("https://tile.openstreetmap.org/{z}/{x}/{y}.png"),
            source.getValue("tiles").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(256, source.getValue("tileSize").jsonPrimitive.int)
        assertEquals(19, source.getValue("maxzoom").jsonPrimitive.int)
    }

    @Test
    fun `attribution travels with the source, as the tile policy requires`() =
        assertEquals("© OpenStreetMap contributors", source.getValue("attribution").jsonPrimitive.content)

    @Test
    fun `the only layer draws that source`() {
        val layer = style.getValue("layers").jsonArray.single().jsonObject
        assertEquals("osm", layer.getValue("source").jsonPrimitive.content)
        assertEquals("raster", layer.getValue("type").jsonPrimitive.content)
    }

    @Test
    fun `the user agent names this app and its id, not a library`() =
        assertEquals("Forager/0.1 (com.zynergy.forager.app)", basemapUserAgent("0.1", "com.zynergy.forager.app"))

    @Test
    fun `an unnamed user agent is refused`() {
        assertFailsWith<IllegalArgumentException> { basemapUserAgent("", "com.zynergy.forager.app") }
    }
}
