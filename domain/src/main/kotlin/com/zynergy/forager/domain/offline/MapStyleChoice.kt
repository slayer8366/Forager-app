package com.zynergy.forager.domain.offline

import com.zynergy.forager.domain.Coordinates

/** When the map draws with the offline style, which is the one saved regions can be read with. */
enum class OfflineStyleMode {
    /** Switch to the offline style when the device loses its connection. */
    WHEN_OFFLINE,

    /** Use the offline style whenever the map is centred inside a saved region, online or not. */
    INSIDE_SAVED_REGIONS,

    /** The user decides, with a prompt when the connection drops. */
    MANUAL,
}

enum class MapStyle { ONLINE, OFFLINE }

/**
 * Picks the style to draw with.
 *
 * Needed at all because a saved region can only be drawn with the style it was downloaded against,
 * which is the offline server's vector style, not the online OpenStreetMap one. So the two look
 * different, and when to change between them is the owner's setting rather than a guess.
 */
fun chooseMapStyle(
    mode: OfflineStyleMode,
    online: Boolean,
    centre: Coordinates,
    savedRegions: List<SavedRegion>,
    manualChoice: MapStyle,
): MapStyle = when (mode) {
    OfflineStyleMode.WHEN_OFFLINE -> if (online) MapStyle.ONLINE else MapStyle.OFFLINE
    OfflineStyleMode.INSIDE_SAVED_REGIONS ->
        if (savedRegions.any { it.complete && it.area.contains(centre) }) MapStyle.OFFLINE else MapStyle.ONLINE
    OfflineStyleMode.MANUAL -> manualChoice
}

/** Whether manual mode should suggest switching: offline, but still drawing the online style. */
fun shouldSuggestOfflineStyle(mode: OfflineStyleMode, online: Boolean, current: MapStyle): Boolean =
    mode == OfflineStyleMode.MANUAL && !online && current == MapStyle.ONLINE
