package com.zynergy.forager.data.basemap

import com.zynergy.forager.domain.BoundingBox

/**
 * The owner's Cloudflare worker, which serves a Protomaps PMTiles extract as ordinary vector tiles.
 *
 * Saved regions are downloaded against [STYLE_URL] and can only be drawn with that same style, so it
 * is a fixed HTTPS URL. The owner's earlier app found that an asset:// style hangs an offline download
 * at 0 of 1, and that a style with text layers crashes it natively. This style has neither, checked on
 * 2026-09-20: 57 fill, line and background layers, no glyphs, no sprite.
 */
object OfflineSource {
    const val STYLE_URL = "https://forager-pmtiles.brandonlee1-894.workers.dev/style/offline.json"
    const val HOST = "forager-pmtiles.brandonlee1-894.workers.dev"
    const val ATTRIBUTION = "Protomaps © OpenStreetMap contributors"

    /** The extract's bounds as its own us.json reported them on 2026-09-20: the continental US. */
    val COVERAGE = BoundingBox(south = 24.4, west = -124.85, north = 49.6, east = -66.87)
}
