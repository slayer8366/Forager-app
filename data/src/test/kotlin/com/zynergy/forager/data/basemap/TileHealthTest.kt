package com.zynergy.forager.data.basemap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TileHealthTest {

    @Test
    fun `a delivered or unchanged tile is healthy`() {
        assertEquals(TileHealth.Healthy, tileHealthFor(200))
        assertEquals(TileHealth.Healthy, tileHealthFor(304))
    }

    @Test
    fun `no response at all is a connection problem`() =
        assertTrue(assertIs<TileHealth.Problem>(tileHealthFor(null)).message.contains("no connection"))

    @Test
    fun `a refusal names OpenStreetMap and the code, and says what still works`() {
        listOf(403, 418, 429).forEach { code ->
            val message = assertIs<TileHealth.Problem>(tileHealthFor(code)).message
            assertTrue(message.contains("refused by OpenStreetMap") && message.contains("$code"), message)
            assertTrue(message.contains("journal and plans still work"), message)
        }
    }

    @Test
    fun `a missing tile is not an outage`() =
        assertEquals(TileHealth.Healthy, tileHealthFor(404))

    @Test
    fun `any other failure reports its code`() =
        assertTrue(assertIs<TileHealth.Problem>(tileHealthFor(502)).message.contains("HTTP 502"))
}

class BasemapNoticeTest {

    @Test
    fun `offline is reported even when no tile request has failed`() {
        val notice = basemapNotice(online = false, health = TileHealth.Healthy)
        assertTrue(notice!!.startsWith("No connection"), notice)
    }

    @Test
    fun `online with a refused tile reports the refusal`() {
        val problem = tileHealthFor(403) as TileHealth.Problem
        assertEquals(problem.message, basemapNotice(online = true, health = problem))
    }

    @Test
    fun `online and healthy says nothing`() {
        assertEquals(null, basemapNotice(online = true, health = TileHealth.Healthy))
        assertEquals(null, basemapNotice(online = true, health = TileHealth.Unknown))
    }
}
