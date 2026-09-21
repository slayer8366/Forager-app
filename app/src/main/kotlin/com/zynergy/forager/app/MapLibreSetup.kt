package com.zynergy.forager.app

import android.content.Context
import org.maplibre.android.MapLibre

/**
 * The one way this app starts MapLibre: the library, then the HTTP client every tile request uses.
 *
 * There is deliberately no storage redirect here. The owner's earlier app, on MapLibre 13.5.0,
 * moved MapLibre's database out of the cache directory before initialising. On 13.6.1 that is both
 * unnecessary and fatal:
 * - FileSource.getDefaultCachePath returns Context.getFilesDir() unless the manifest opts into
 *   external storage (read from the 13.6.1 bytecode on 2026-09-20). The database was found at
 *   files/mbgl-offline.db on the emulator before any redirect existed.
 * - setResourcesCachePath now requires MapLibre.getInstance to have run first. Calling it before,
 *   in the earlier app's order, threw MapLibreConfigurationException on the emulator.
 */
fun initializeMapLibre(context: Context) {
    val app = context.applicationContext
    MapLibre.getInstance(app)
    BasemapHttp.install(app)
}
