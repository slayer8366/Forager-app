package com.zynergy.forager.app

import android.content.Context
import android.util.Log
import org.maplibre.android.MapLibre
import org.maplibre.android.storage.FileSource
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private val storageRedirected = AtomicBoolean(false)

/**
 * The one way this app starts MapLibre. Every caller goes through here, never MapLibre.getInstance.
 *
 * MapLibre keeps saved regions in its database, and by default that database lives in the cache
 * directory, which Android may clear under storage pressure without asking. The owner's earlier app
 * confirmed on hardware that saved regions counted as cache. So the database is moved under files/
 * before MapLibre first opens it. Doing that in one function means no call site can get the order
 * wrong, which is how the earlier app missed it once.
 */
fun initializeMapLibre(context: Context) {
    val app = context.applicationContext
    if (storageRedirected.compareAndSet(false, true)) {
        val dir = File(app.filesDir, "maplibre").apply { mkdirs() }
        FileSource.setResourcesCachePath(
            app,
            dir.absolutePath,
            object : FileSource.ResourcesCachePathChangeCallback {
                override fun onSuccess(path: String) {
                    Log.i("ForagerMapStorage", "MapLibre storage at $path")
                }

                override fun onError(message: String) {
                    // Not fatal, MapLibre keeps its previous path, but saved regions are then at risk.
                    Log.w("ForagerMapStorage", "Could not move MapLibre storage out of cache: $message")
                }
            },
        )
    }
    MapLibre.getInstance(app)
    BasemapHttp.install(app)
}
