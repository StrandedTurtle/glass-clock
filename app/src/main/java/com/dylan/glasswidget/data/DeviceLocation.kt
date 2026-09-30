package com.dylan.glasswidget.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Coarse device location via the platform LocationManager (no Play Services). */
object DeviceLocation {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Cached fix, freshest first across providers. Safe from the background worker, though
     * Android may withhold it from background apps — callers fall back to the last stored fix.
     */
    @SuppressLint("MissingPermission")
    fun lastKnown(context: Context): Location? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        return runCatching {
            lm.getProviders(true)
                .mapNotNull { lm.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
                ?.let { Location(it.latitude, it.longitude) }
        }.getOrNull()
    }

    /** Foreground: use the cached fix if there is one, otherwise ask the network provider for a fresh one. */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context, timeoutMs: Long = 10_000): Location? {
        lastKnown(context)?.let { return it }
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = lm.getProviders(true).firstOrNull { it == LocationManager.NETWORK_PROVIDER }
            ?: lm.getProviders(true).firstOrNull()
            ?: return null
        val executor = Executors.newSingleThreadExecutor()
        return try {
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = android.os.CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    runCatching {
                        lm.getCurrentLocation(provider, signal, executor) { loc ->
                            if (cont.isActive) cont.resume(loc?.let { Location(it.latitude, it.longitude) })
                        }
                    }.onFailure { if (cont.isActive) cont.resume(null) }
                }
            }
        } finally {
            executor.shutdown()
        }
    }
}
