package com.zynergy.forager.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether the device currently has a network that can reach the internet.
 *
 * Needed because MapLibre goes quiet when offline instead of failing requests, so the tile watcher
 * cannot see that case. A device without a ConnectivityManager is reported online, since claiming
 * offline would put a false warning over a map that may well be loading.
 */
fun Context.networkOnline(): Flow<Boolean> = callbackFlow {
    val manager = ContextCompat.getSystemService(this@networkOnline, ConnectivityManager::class.java)
    if (manager == null) {
        trySend(true)
        awaitClose { }
        return@callbackFlow
    }
    fun emit(event: String, online: Boolean) {
        Log.i("ForagerNetwork", "$event -> online=$online")
        trySend(online)
    }
    // The state comes from each callback's own arguments. Asking the manager for the active network
    // from inside onLost can still return the network that is going away, which left the app
    // reporting online in airplane mode on the emulator.
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = emit("available", true)
        override fun onLost(network: Network) = emit("lost", false)
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
            emit("capabilities", caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
    }
    emit(
        "initial",
        manager.getNetworkCapabilities(manager.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
    )
    manager.registerDefaultNetworkCallback(callback)
    awaitClose { manager.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()
