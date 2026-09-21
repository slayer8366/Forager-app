package com.zynergy.forager.app

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zynergy.forager.data.basemap.OsmRasterStyle
import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Coordinates
import com.zynergy.forager.presentation.MapOverlay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

private const val AREA_SOURCE = "planning-area"
private const val RING_SOURCE = "accuracy-rings"
private const val MARKER_SOURCE = "entry-markers"

/**
 * The basemap, with the planning area and journal entries drawn over it.
 *
 * Hosted as a plain MapView, the way the owner's earlier app did after trying the alternatives. The
 * style is set exactly once, because every setStyle call wipes all sources and layers and flashes
 * the map blank; afterwards only the GeoJSON sources are updated. The camera is positioned once,
 * on the planning area, and never pushed again, so moving the area does not yank the view away from
 * wherever the user has panned to.
 *
 * Unlike that earlier app, the MapView's whole lifecycle is driven, including onStart and onStop,
 * which it never called.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun BasemapView(
    overlay: MapOverlay,
    initialArea: BoundingBox,
    onTap: (Coordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnTap by rememberUpdatedState(onTap)
    var styled by remember { mutableStateOf<Pair<MapLibreMap, Style>?>(null) }

    val mapView = remember {
        MapLibre.getInstance(context)
        BasemapHttp.install(context)
        MapView(context).apply {
            onCreate(null)
            // The screen scrolls vertically, and a scrolling parent otherwise takes over any drag
            // that starts on the map. Re-asserted on every press, because a parent resets it.
            setOnTouchListener { view, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                }
                false
            }
            getMapAsync { map ->
                map.setMaxZoomPreference(OsmRasterStyle.MAX_ZOOM.toDouble())
                map.uiSettings.isCompassEnabled = false
                map.addOnMapClickListener { at ->
                    currentOnTap(Coordinates(at.latitude, at.longitude))
                    false
                }
                map.setStyle(Style.Builder().fromJson(OsmRasterStyle.json())) { style ->
                    addOverlayLayers(style)
                    map.moveCamera(CameraUpdateFactory.newLatLngBounds(initialArea.toLatLngBounds(), 48))
                    styled = map to style
                }
            }
        }
    }

    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        fun start() { if (!started) { mapView.onStart(); started = true } }
        fun resume() { start(); if (!resumed) { mapView.onResume(); resumed = true } }
        fun pause() { if (resumed) { mapView.onPause(); resumed = false } }
        fun stop() { pause(); if (started) { mapView.onStop(); started = false } }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE -> pause()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        // Catch up with wherever the host already is, since the observer only sees later events.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            stop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(styled, overlay) {
        val (_, style) = styled ?: return@LaunchedEffect
        style.getSourceAs<GeoJsonSource>(AREA_SOURCE)?.setGeoJson(
            Feature.fromGeometry(Polygon.fromLngLats(listOf(overlay.planningArea.map { it.toPoint() }))),
        )
        style.getSourceAs<GeoJsonSource>(RING_SOURCE)?.setGeoJson(
            FeatureCollection.fromFeatures(
                overlay.markers.mapNotNull { m ->
                    m.accuracyRing?.let { Feature.fromGeometry(Polygon.fromLngLats(listOf(it.map { p -> p.toPoint() }))) }
                },
            ),
        )
        style.getSourceAs<GeoJsonSource>(MARKER_SOURCE)?.setGeoJson(
            FeatureCollection.fromFeatures(
                overlay.markers.map { m ->
                    Feature.fromGeometry(m.at.toPoint()).apply { addBooleanProperty("precise", m.precise) }
                },
            ),
        )
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.testTag("basemap"))
        // Always visible, not behind MapLibre's tap-to-reveal button: OSM's policy asks for the
        // attribution to be shown clearly on the map.
        Text(
            OsmRasterStyle.ATTRIBUTION,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(Color(0xCCFFFFFF))
                .padding(horizontal = 4.dp, vertical = 1.dp)
                .testTag("osm-attribution"),
        )
    }
}

private fun addOverlayLayers(style: Style) {
    style.addSource(GeoJsonSource(AREA_SOURCE))
    style.addSource(GeoJsonSource(RING_SOURCE))
    style.addSource(GeoJsonSource(MARKER_SOURCE))

    style.addLayer(
        FillLayer("planning-area-fill", AREA_SOURCE)
            .withProperties(PropertyFactory.fillColor("#2E7D32"), PropertyFactory.fillOpacity(0.12f)),
    )
    style.addLayer(
        LineLayer("planning-area-edge", AREA_SOURCE)
            .withProperties(PropertyFactory.lineColor("#2E7D32"), PropertyFactory.lineWidth(2f)),
    )
    style.addLayer(
        FillLayer("accuracy-ring-fill", RING_SOURCE)
            .withProperties(PropertyFactory.fillColor("#1B5E20"), PropertyFactory.fillOpacity(0.18f)),
    )
    style.addLayer(
        CircleLayer("entry-precise", MARKER_SOURCE)
            .withFilter(Expression.eq(Expression.get("precise"), Expression.literal(true)))
            .withProperties(
                PropertyFactory.circleColor("#1B5E20"),
                PropertyFactory.circleRadius(6f),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(1.5f),
            ),
    )
    // A coarse or unmeasured fix is a hollow ring, so it never reads as a confident point.
    style.addLayer(
        CircleLayer("entry-coarse", MARKER_SOURCE)
            .withFilter(Expression.eq(Expression.get("precise"), Expression.literal(false)))
            .withProperties(
                PropertyFactory.circleOpacity(0f),
                PropertyFactory.circleRadius(6f),
                PropertyFactory.circleStrokeColor("#8A6D00"),
                PropertyFactory.circleStrokeWidth(2.5f),
            ),
    )
}

private fun Coordinates.toPoint(): Point = Point.fromLngLat(longitude, latitude)

private fun BoundingBox.toLatLngBounds(): LatLngBounds =
    LatLngBounds.Builder().include(LatLng(south, west)).include(LatLng(north, east)).build()
