package com.zynergy.forager.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.zynergy.forager.domain.Coordinates
import com.zynergy.forager.domain.Fix
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.port.LocationSource
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * A single position from the platform's own location service.
 *
 * Uses [LocationManager] rather than Play Services so the app carries no Google dependency for
 * something the platform already does. A forager wants one fix when they save an entry, not a
 * stream, so there is no subscription to leak.
 *
 * Three things this refuses to do, each of which would otherwise produce a confident wrong answer:
 *
 * - **Pass on a stale cached position.** The last known location can be hours old, from wherever
 *   the phone last had a clear view. Attached to a find it would place it at the trailhead. A
 *   cached fix older than [MAX_CACHED_AGE_MILLIS] is ignored and a fresh one requested instead.
 * - **Report a fix with no accuracy as though it were exact.** A [Location] without
 *   `hasAccuracy()` gives no radius, and inventing one would defeat the point of carrying it.
 * - **Wait forever.** Under canopy a fix may never arrive. After [FIX_TIMEOUT_MILLIS] the answer
 *   is that no fix was obtained, which is a thing the user can act on.
 */
class AndroidLocationSource(private val context: Context) : LocationSource {

    private val manager: LocationManager? =
        ContextCompat.getSystemService(context, LocationManager::class.java)

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun currentFix(): Outcome<Fix> {
        val manager = manager
            ?: return Outcome.Unsupported("location services (this device reports none)")

        if (!hasPermission()) {
            return Outcome.Failed("location permission has not been granted")
        }
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
            !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        ) {
            return Outcome.Failed("location is switched off in system settings")
        }

        freshCachedFix(manager)?.let { return Outcome.Ok(it) }

        val location = withTimeoutOrNull(FIX_TIMEOUT_MILLIS) { awaitSingleUpdate(manager) }
            ?: return Outcome.Failed(
                "no fix within ${FIX_TIMEOUT_MILLIS / 1000} seconds; tree cover and buildings both do this",
            )

        return location.toFix()
            ?: Outcome.Failed("the device reported a position with no accuracy, so it cannot be placed")
    }

    /** The cached position, but only if it is recent enough to still describe where the user is. */
    private fun freshCachedFix(manager: LocationManager): Fix? {
        if (!hasPermission()) return null
        val now = System.currentTimeMillis()
        return providers
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .filter { now - it.time <= MAX_CACHED_AGE_MILLIS }
            .mapNotNull { location -> location.toFix().let { (it as? Outcome.Ok)?.value } }
            .minByOrNull { it.accuracyMetres ?: Double.MAX_VALUE }
    }

    private suspend fun awaitSingleUpdate(manager: LocationManager): Location? =
        suspendCancellableCoroutine { continuation ->
            val provider = providers.firstOrNull { manager.isProviderEnabled(it) }
            if (provider == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            val listener = SingleUpdateListener(continuation)
            try {
                manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            } catch (_: SecurityException) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            continuation.invokeOnCancellation {
                runCatching { manager.removeUpdates(listener) }
            }
            listener.onDone = { runCatching { manager.removeUpdates(listener) } }
        }

    private fun Location.toFix(): Outcome<Fix> {
        if (!hasAccuracy() || accuracy <= 0f) {
            return Outcome.Failed("the device reported a position with no accuracy")
        }
        return Outcome.Ok(
            Fix(Coordinates(latitude, longitude), accuracyMetres = accuracy.toDouble()),
        )
    }

    private companion object {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

        /** Older than this and a cached position describes where the phone was, not where it is. */
        const val MAX_CACHED_AGE_MILLIS = 60_000L

        /** Long enough for a cold GPS lock under open sky, short enough not to feel broken. */
        const val FIX_TIMEOUT_MILLIS = 20_000L
    }
}

private class SingleUpdateListener(
    private val continuation: CancellableContinuation<Location?>,
) : android.location.LocationListener {

    var onDone: (() -> Unit)? = null
    private var delivered = false

    override fun onLocationChanged(location: Location) {
        if (delivered) return
        delivered = true
        onDone?.invoke()
        if (continuation.isActive) continuation.resume(location)
    }

    @Deprecated("Required by the pre-API-29 interface", ReplaceWith(""))
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit

    override fun onProviderDisabled(provider: String) {
        if (delivered) return
        delivered = true
        onDone?.invoke()
        if (continuation.isActive) continuation.resume(null)
    }

    override fun onProviderEnabled(provider: String) = Unit
}
