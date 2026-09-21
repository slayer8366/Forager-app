package com.zynergy.forager.domain.offline

import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Coordinates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The offline worker's own bounds, from its us.json on 2026-09-20. */
private val US = BoundingBox(south = 24.4, west = -124.85, north = 49.6, east = -66.87)
private val PLANNING_AREA = BoundingBox(south = 47.0, west = -123.0, north = 47.9, east = -122.1)
private val SMALL_AREA = BoundingBox(south = 47.44, west = -122.42, north = 47.46, east = -122.38)
private val WORLD = BoundingBox(south = -85.0, west = -180.0, north = 85.0, east = 179.999)

class TileMathTest {

    @Test
    fun `the whole world is 1 tile at zoom 0 and 4 to the power z after that`() {
        assertEquals(1, TileMath.tilesAt(WORLD, 0))
        assertEquals(16, TileMath.tilesAt(WORLD, 2))
        assertEquals(1024, TileMath.tilesAt(WORLD, 5))
    }

    @Test
    fun `an area inside a single tile counts one tile`() {
        val speck = BoundingBox(south = 47.450, west = -122.401, north = 47.451, east = -122.400)
        assertEquals(1, TileMath.tilesAt(speck, 10))
    }

    @Test
    fun `the tile at the emulator's fix is the one the worker served`() {
        // 2621/5732 at zoom 14 returned tile data from the worker on 2026-09-20.
        assertEquals(2621, TileMath.tileX(-122.4, 14))
        assertEquals(5732, TileMath.tileY(47.45, 14))
    }

    @Test
    fun `north has the smaller row number`() =
        assertTrue(TileMath.tileY(48.0, 12) < TileMath.tileY(47.0, 12))

    @Test
    fun `a range sums its levels`() =
        assertEquals(
            (0..6).sumOf { TileMath.tilesAt(PLANNING_AREA, it) },
            TileMath.tilesBetween(PLANNING_AREA, 0, 6),
        )

    @Test
    fun `the default planning area at full detail is over the allowance`() =
        assertTrue(TileMath.tilesBetween(PLANNING_AREA, 0, 15) > PlanOfflineDownload.ALLOWANCE_TILES)
}

class PlanOfflineDownloadTest {

    private val plan = PlanOfflineDownload(US)

    @Test
    fun `a small area fits at full detail`() {
        val result = assertIs<OfflineDownloadPlan.FullDetail>(plan(SMALL_AREA, tilesAlreadyUsed = 0))
        assertEquals(15, result.maxZoom)
        assertEquals(TileMath.tilesBetween(SMALL_AREA, 0, 15), result.tiles)
    }

    @Test
    fun `a planning-sized area gets the most detail that fits, and no more`() {
        val result = assertIs<OfflineDownloadPlan.ReducedDetail>(plan(PLANNING_AREA, tilesAlreadyUsed = 0))
        assertTrue(result.tiles <= 6000, "must fit: ${result.tiles}")
        assertTrue(
            TileMath.tilesBetween(PLANNING_AREA, 0, result.maxZoom + 1) > 6000,
            "one more level must not fit, or this was not the most detail",
        )
        assertEquals(TileMath.tilesBetween(PLANNING_AREA, 0, 15), result.fullDetailTiles)
    }

    @Test
    fun `tiles already used come off the allowance`() {
        val fresh = plan(SMALL_AREA, tilesAlreadyUsed = 0) as OfflineDownloadPlan.FullDetail
        val tight = plan(SMALL_AREA, tilesAlreadyUsed = 6000 - fresh.tiles + 1)
        assertFalse(tight is OfflineDownloadPlan.FullDetail, "one tile short must not fit at full detail")
    }

    @Test
    fun `with the allowance spent, nothing fits`() {
        val result = assertIs<OfflineDownloadPlan.TooLarge>(plan(SMALL_AREA, tilesAlreadyUsed = 6000))
        assertEquals(0, result.remaining)
    }

    @Test
    fun `an area the offline server has no tiles for is refused as such`() {
        val london = BoundingBox(south = 51.4, west = -0.2, north = 51.6, east = 0.0)
        assertEquals(OfflineDownloadPlan.OutsideCoverage, plan(london, tilesAlreadyUsed = 0))
    }

    @Test
    fun `an area straddling the edge of coverage is flagged`() {
        val border = BoundingBox(south = 49.0, west = -123.0, north = 49.9, east = -122.0)
        assertTrue(plan.partlyOutsideCoverage(border))
        assertFalse(plan.partlyOutsideCoverage(PLANNING_AREA))
    }
}

class MapStyleChoiceTest {

    private val inside = Coordinates(47.45, -122.4)
    private val outside = Coordinates(40.0, -100.0)
    private val region = SavedRegion(1, "Puget", PLANNING_AREA, 13, 4000, complete = true)

    @Test
    fun `when-offline mode follows the connection`() {
        assertEquals(MapStyle.ONLINE, chooseMapStyle(OfflineStyleMode.WHEN_OFFLINE, true, inside, listOf(region), MapStyle.ONLINE))
        assertEquals(MapStyle.OFFLINE, chooseMapStyle(OfflineStyleMode.WHEN_OFFLINE, false, outside, emptyList(), MapStyle.ONLINE))
    }

    @Test
    fun `inside-regions mode follows the map centre, online or not`() {
        assertEquals(MapStyle.OFFLINE, chooseMapStyle(OfflineStyleMode.INSIDE_SAVED_REGIONS, true, inside, listOf(region), MapStyle.ONLINE))
        assertEquals(MapStyle.ONLINE, chooseMapStyle(OfflineStyleMode.INSIDE_SAVED_REGIONS, false, outside, listOf(region), MapStyle.ONLINE))
    }

    @Test
    fun `an unfinished region does not switch the style`() {
        val partial = region.copy(complete = false)
        assertEquals(MapStyle.ONLINE, chooseMapStyle(OfflineStyleMode.INSIDE_SAVED_REGIONS, true, inside, listOf(partial), MapStyle.ONLINE))
    }

    @Test
    fun `manual mode does what the user picked and suggests only when offline on the online style`() {
        assertEquals(MapStyle.OFFLINE, chooseMapStyle(OfflineStyleMode.MANUAL, true, outside, emptyList(), MapStyle.OFFLINE))
        assertTrue(shouldSuggestOfflineStyle(OfflineStyleMode.MANUAL, online = false, current = MapStyle.ONLINE))
        assertFalse(shouldSuggestOfflineStyle(OfflineStyleMode.MANUAL, online = true, current = MapStyle.ONLINE))
        assertFalse(shouldSuggestOfflineStyle(OfflineStyleMode.WHEN_OFFLINE, online = false, current = MapStyle.ONLINE))
    }

    @Test
    fun `tiles used counts unfinished regions too`() =
        assertEquals(5000, listOf(region, region.copy(id = 2, tiles = 1000, complete = false)).tilesUsed())
}
