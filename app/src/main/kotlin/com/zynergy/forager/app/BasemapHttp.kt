package com.zynergy.forager.app

import android.content.Context
import com.zynergy.forager.data.basemap.TileHealth
import com.zynergy.forager.data.basemap.basemapUserAgent
import com.zynergy.forager.data.basemap.tileHealthFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import org.maplibre.android.module.http.HttpRequestUtil
import java.io.IOException

/**
 * The HTTP client MapLibre uses for every tile, installed once per process.
 *
 * Two jobs, both required by OpenStreetMap's tile policy. It replaces MapLibre's default
 * User-Agent, which the policy forbids, with one naming this app. And it watches the tile server's
 * answers, because MapLibre does not tell the app when tiles fail. Only the request's User-Agent is
 * touched: the response's caching headers pass through unchanged, since the policy requires them
 * to be honoured and MapLibre's own cache is what honours them.
 */
object BasemapHttp {

    private const val OSM_TILE_HOST = "tile.openstreetmap.org"

    private val _health = MutableStateFlow<TileHealth>(TileHealth.Unknown)
    val health: StateFlow<TileHealth> = _health.asStateFlow()

    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val agent = basemapUserAgent(versionNameOf(context), context.packageName)
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder().header("User-Agent", agent).build()
                    val watched = request.url.host == OSM_TILE_HOST
                    try {
                        chain.proceed(request).also { if (watched) _health.value = tileHealthFor(it.code) }
                    } catch (e: IOException) {
                        if (watched) _health.value = tileHealthFor(null)
                        throw e
                    }
                }
                .build()
            HttpRequestUtil.setOkHttpClient(client)
            installed = true
        }
    }

    private fun versionNameOf(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
}
