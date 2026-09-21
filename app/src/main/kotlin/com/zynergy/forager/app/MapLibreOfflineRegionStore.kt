package com.zynergy.forager.app

import android.content.Context
import android.util.Log
import com.zynergy.forager.data.basemap.OfflineSource
import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.offline.DownloadProgress
import com.zynergy.forager.domain.offline.OfflineRegionStore
import com.zynergy.forager.domain.offline.SavedRegion
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import kotlin.coroutines.resume

/**
 * Saved regions kept in MapLibre's own offline database, which is the only place MapLibre can read
 * them from. It is the single source of truth: the app's name, detail level and tile reservation
 * travel in each region's metadata rather than in a second table that could disagree with it.
 */
class MapLibreOfflineRegionStore(private val context: Context) : OfflineRegionStore {

    private val manager: OfflineManager by lazy {
        initializeMapLibre(context)
        OfflineManager.getInstance(context)
    }

    override suspend fun list(): Outcome<List<SavedRegion>> {
        val regions = suspendCancellableCoroutine<List<OfflineRegion>?> { cont ->
            manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) = cont.resume(offlineRegions?.toList() ?: emptyList())
                override fun onError(error: String) {
                    Log.w(TAG, "listing offline regions failed: $error")
                    cont.resume(null)
                }
            })
        } ?: return Outcome.Failed("could not read saved offline regions")
        val saved = regions.mapNotNull { region: OfflineRegion ->
            val complete = statusOf(region)?.isComplete == true
            region.toSaved(complete)
        }
        return Outcome.Ok(saved)
    }

    override suspend fun download(
        name: String,
        area: BoundingBox,
        maxZoom: Int,
        reservedTiles: Long,
        onProgress: (DownloadProgress) -> Unit,
    ): Outcome<SavedRegion> {
        val definition = OfflineTilePyramidRegionDefinition(
            OfflineSource.STYLE_URL,
            LatLngBounds.Builder().include(LatLng(area.south, area.west)).include(LatLng(area.north, area.east)).build(),
            0.0,
            maxZoom.toDouble(),
            context.resources.displayMetrics.density,
            false,
        )
        val metadata = JSONObject().put("name", name).put("maxZoom", maxZoom).put("tiles", reservedTiles)
            .toString().toByteArray()

        val region = suspendCancellableCoroutine<OfflineRegion?> { cont ->
            manager.createOfflineRegion(definition, metadata, object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) = cont.resume(offlineRegion)
                override fun onError(error: String) {
                    Log.w(TAG, "creating offline region failed: $error")
                    cont.resume(null)
                }
            })
        } ?: return Outcome.Failed("could not start the download")

        return suspendCancellableCoroutine<Outcome<SavedRegion>> { cont ->
            region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                override fun onStatusChanged(status: OfflineRegionStatus) {
                    onProgress(DownloadProgress(status.completedResourceCount, status.requiredResourceCount))
                    if (status.isComplete && cont.isActive) {
                        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                        // The count MapLibre actually fetched, against the one reserved from TileMath.
                        Log.i(TAG, "region $name complete: ${status.completedTileCount} tiles fetched, $reservedTiles reserved")
                        cont.resume(Outcome.Ok(SavedRegion(region.id, name, area, maxZoom, reservedTiles, complete = true)))
                    }
                }

                // MapLibre retries after these on its own, so they are logged rather than ending the download.
                override fun onError(error: OfflineRegionError) {
                    Log.w(TAG, "offline download error for $name: ${error.reason} ${error.message}")
                }

                override fun mapboxTileCountLimitExceeded(limit: Long) {
                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    if (cont.isActive) cont.resume(Outcome.Failed("MapLibre's own tile limit of $limit was reached"))
                }
            })
            cont.invokeOnCancellation { region.setDownloadState(OfflineRegion.STATE_INACTIVE) }
            region.setDownloadState(OfflineRegion.STATE_ACTIVE)
        }
    }

    override suspend fun delete(id: Long): Outcome<Unit> {
        val region = suspendCancellableCoroutine<OfflineRegion?> { cont ->
            manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) = cont.resume(offlineRegions?.firstOrNull { it.id == id })
                override fun onError(error: String) = cont.resume(null)
            })
        } ?: return Outcome.Failed("that saved region no longer exists")
        return suspendCancellableCoroutine<Outcome<Unit>> { cont ->
            region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                override fun onDelete() = cont.resume(Outcome.Ok(Unit))
                override fun onError(error: String) = cont.resume(Outcome.Failed("could not delete the region: $error"))
            })
        }
    }

    private suspend fun statusOf(region: OfflineRegion): OfflineRegionStatus? = suspendCancellableCoroutine<OfflineRegionStatus?> { cont ->
        region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) = cont.resume(status)
            override fun onError(error: String?) = cont.resume(null)
        })
    }

    /** A region whose metadata cannot be read was not made by this app, and is left out rather than guessed at. */
    private fun OfflineRegion.toSaved(complete: Boolean): SavedRegion? {
        val def = definition as? OfflineTilePyramidRegionDefinition ?: return null
        val meta = runCatching { JSONObject(String(metadata)) }.getOrElse {
            Log.w(TAG, "offline region $id has unreadable metadata; not listed")
            return null
        }
        val b = def.bounds ?: return null
        return SavedRegion(
            id = id,
            name = meta.optString("name", "Saved area"),
            area = BoundingBox(south = b.latitudeSouth, west = b.longitudeWest, north = b.latitudeNorth, east = b.longitudeEast),
            maxZoom = meta.optInt("maxZoom", def.maxZoom.toInt()),
            tiles = meta.optLong("tiles", 0),
            complete = complete,
        )
    }

    private companion object { const val TAG = "ForagerOffline" }
}
