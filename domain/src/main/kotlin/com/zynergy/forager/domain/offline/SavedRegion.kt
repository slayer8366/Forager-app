package com.zynergy.forager.domain.offline

import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Outcome

/**
 * An area saved for offline use.
 *
 * [tiles] is the count reserved against the allowance when the download was planned. An
 * unfinished download keeps its reservation, so starting several and abandoning them cannot slip
 * past the limit. Deleting the region is what gives the tiles back.
 */
data class SavedRegion(
    val id: Long,
    val name: String,
    val area: BoundingBox,
    val maxZoom: Int,
    val tiles: Long,
    val complete: Boolean,
)

/** Progress of a download, for a progress bar. [required] may grow while MapLibre discovers resources. */
data class DownloadProgress(val completed: Long, val required: Long)

/** Saved regions, behind an interface the app owns rather than the map SDK's own types. */
interface OfflineRegionStore {
    suspend fun list(): Outcome<List<SavedRegion>>

    suspend fun download(
        name: String,
        area: BoundingBox,
        maxZoom: Int,
        reservedTiles: Long,
        onProgress: (DownloadProgress) -> Unit,
    ): Outcome<SavedRegion>

    suspend fun delete(id: Long): Outcome<Unit>
}

/** Tiles already committed to saved regions, complete or not. */
fun List<SavedRegion>.tilesUsed(): Long = sumOf { it.tiles }
