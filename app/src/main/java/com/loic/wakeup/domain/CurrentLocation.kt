package com.loic.wakeup.domain

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Location permission state as the failsafe needs it. */
object LocationAccess {
    /** Precise location — approximate (~2 km) is useless against a 100 m radius. */
    fun hasPrecise(context: Context): Boolean = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    /**
     * Checks run from an alarm while the app is in the background, which on Android 10+ needs
     * "Allow all the time". Below that, foreground access already covers it.
     */
    fun hasBackground(context: Context): Boolean =
        hasPrecise(context) && (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            )

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

/**
 * One-shot "where is the device right now" on the framework [LocationManager] (the app has no
 * Play services dependency). Asks every enabled provider in parallel and keeps the most accurate
 * fix; if none arrives in time, falls back to a recent last-known fix.
 */
object CurrentLocation {
    private const val FIX_TIMEOUT_MS = 30_000L
    private const val MAX_LAST_KNOWN_AGE_MS = 10 * 60_000L
    private const val GOOD_ENOUGH_ACCURACY_METERS = 30f

    /** Null when location is off, precise permission is missing, or no usable fix was found. */
    @SuppressLint("MissingPermission") // guarded by LocationAccess.hasPrecise
    suspend fun get(context: Context): Location? {
        if (!LocationAccess.hasPrecise(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        if (!LocationManagerCompat.isLocationEnabled(lm)) return null

        val enabled = lm.getProviders(true)
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }.filter { it in enabled }

        freshFixes(context, lm, providers).mostAccurate()?.let { return it }

        val now = SystemClock.elapsedRealtimeNanos()
        return providers
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { (now - it.elapsedRealtimeNanos) / 1_000_000 <= MAX_LAST_KNOWN_AGE_MS }
            .mostAccurate()
    }

    /**
     * Requests a fix from every provider at once. Stops early once one is accurate enough to decide
     * on — GPS indoors can otherwise hold everything up for the full timeout.
     */
    private suspend fun freshFixes(
        context: Context,
        lm: LocationManager,
        providers: List<String>,
    ): List<Location> = coroutineScope {
        val results = Channel<Location?>(Channel.UNLIMITED)
        val requests = providers.map { provider ->
            launch { results.send(withTimeoutOrNull(FIX_TIMEOUT_MS) { lm.awaitFix(context, provider) }) }
        }
        val fixes = mutableListOf<Location>()
        for (i in providers.indices) {
            val fix = results.receive() ?: continue
            fixes += fix
            if (fix.hasAccuracy() && fix.accuracy <= GOOD_ENOUGH_ACCURACY_METERS) break
        }
        requests.forEach { it.cancel() }
        fixes
    }

    @SuppressLint("MissingPermission")
    private suspend fun LocationManager.awaitFix(context: Context, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            LocationManagerCompat.getCurrentLocation(
                this, provider, signal, ContextCompat.getMainExecutor(context),
            ) { location -> if (cont.isActive) cont.resume(location) }
        }

    private fun List<Location>.mostAccurate(): Location? =
        minByOrNull { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE }
}
