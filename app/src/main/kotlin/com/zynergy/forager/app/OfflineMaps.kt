package com.zynergy.forager.app

import com.zynergy.forager.data.basemap.OfflineSource
import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.offline.DownloadProgress
import com.zynergy.forager.domain.offline.MapStyle
import com.zynergy.forager.domain.offline.OfflineDownloadPlan
import com.zynergy.forager.domain.offline.OfflineRegionStore
import com.zynergy.forager.domain.offline.PlanOfflineDownload
import com.zynergy.forager.domain.offline.SavedRegion
import com.zynergy.forager.domain.offline.tilesUsed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Saved regions and the one download in progress, held for the life of the app process.
 *
 * App-scoped rather than screen-scoped, so leaving the Map tab does not cancel a download. It does
 * not survive the process being killed: a download stops and the region stays unfinished, still
 * holding its tiles, until it is deleted or started again.
 */
class OfflineMaps(private val store: OfflineRegionStore, private val scope: CoroutineScope) {

    private val planner = PlanOfflineDownload(OfflineSource.COVERAGE)

    private val _regions = MutableStateFlow<List<SavedRegion>>(emptyList())
    val regions: StateFlow<List<SavedRegion>> = _regions.asStateFlow()

    private val _progress = MutableStateFlow<DownloadProgress?>(null)
    val progress: StateFlow<DownloadProgress?> = _progress.asStateFlow()

    private val _problem = MutableStateFlow<String?>(null)
    val problem: StateFlow<String?> = _problem.asStateFlow()

    /** The manual-mode choice. Kept for the session, like any other user-set map state. */
    val manualStyle = MutableStateFlow(MapStyle.ONLINE)

    private var download: Job? = null
    val downloading: Boolean get() = download?.isActive == true

    fun refresh() {
        scope.launch {
            when (val outcome = store.list()) {
                is Outcome.Ok -> _regions.value = outcome.value
                is Outcome.Partial -> { _regions.value = outcome.value; _problem.value = outcome.note }
                is Outcome.Failed -> _problem.value = outcome.reason
                is Outcome.Unsupported -> _problem.value = "offline maps are not supported on this device"
            }
        }
    }

    fun plan(area: BoundingBox): OfflineDownloadPlan = planner(area, _regions.value.tilesUsed())

    fun partlyOutsideCoverage(area: BoundingBox): Boolean = planner.partlyOutsideCoverage(area)

    fun save(name: String, area: BoundingBox) {
        if (downloading) return
        val plan = plan(area)
        val (maxZoom, tiles) = when (plan) {
            is OfflineDownloadPlan.FullDetail -> plan.maxZoom to plan.tiles
            is OfflineDownloadPlan.ReducedDetail -> plan.maxZoom to plan.tiles
            else -> return
        }
        _problem.value = null
        _progress.value = DownloadProgress(0, 0)
        download = scope.launch {
            val outcome = store.download(name, area, maxZoom, tiles) { _progress.value = it }
            _progress.value = null
            if (outcome is Outcome.Failed) _problem.value = outcome.reason
            refresh()
        }
    }

    fun delete(id: Long) {
        scope.launch {
            val outcome = store.delete(id)
            if (outcome is Outcome.Failed) _problem.value = outcome.reason
            refresh()
        }
    }
}
