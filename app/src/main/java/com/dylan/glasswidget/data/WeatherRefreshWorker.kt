package com.dylan.glasswidget.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dylan.glasswidget.widget.GlassClockWidget
import com.dylan.glasswidget.widget.WidgetPrefsKeys
import com.dylan.glasswidget.widget.WidgetSettings
import java.util.concurrent.TimeUnit

/**
 * Fetches weather for every distinct location the placed widgets want, writes the shared cache,
 * then asks the widgets to redraw. The widget itself never touches the network.
 */
class WeatherRefreshWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val ids = GlanceAppWidgetManager(ctx).getGlanceIds(GlassClockWidget::class.java)
        if (ids.isEmpty()) return Result.success()

        val wanted = LinkedHashMap<String, Location>()
        for (id in ids) {
            var settings = WidgetSettings.from(getAppWidgetState(ctx, PreferencesGlanceStateDefinition, id))
            if (settings.locationMode == LocationMode.Device) {
                // Opportunistically keep the stored fix fresh; fall back to the last one if Android says no.
                DeviceLocation.lastKnown(ctx)?.let { fix ->
                    if (LocationKey.of(fix.lat, fix.lon) != settings.location?.key) {
                        updateAppWidgetState(ctx, id) { p: MutablePreferences ->
                            p[WidgetPrefsKeys.DEVICE_LAT] = fix.lat
                            p[WidgetPrefsKeys.DEVICE_LON] = fix.lon
                        }
                        settings = settings.copy(deviceLat = fix.lat, deviceLon = fix.lon)
                    }
                }
            }
            settings.location?.let { wanted.putIfAbsent(it.key, it) }
        }

        val api = OpenMeteoApi()
        val fresh = LinkedHashMap<String, WeatherSnapshot>()
        var failures = 0
        for ((key, loc) in wanted) {
            runCatching { api.fetchCurrent(loc.lat, loc.lon) }
                .onSuccess { fresh[key] = it }
                .onFailure { failures++ }
        }

        WeatherCache.save(ctx, fresh)
        GlassClockWidget().updateAll(ctx)

        // Back off and retry a few times on failure; after that wait for the next periodic run.
        return if (failures > 0 && fresh.isEmpty() && runAttemptCount < 4) Result.retry() else Result.success()
    }

    companion object {
        private const val PERIODIC_NAME = "weather_refresh"
        private const val ONE_SHOT_NAME = "weather_refresh_now"

        private val networkConstraint =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Every 30 minutes; KEEP makes repeated calls (one per widget, every launch) a no-op. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeatherRefreshWorker>(30, TimeUnit.MINUTES)
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        /** Fetch right now (new widget, changed city, manual button). Replaces a pending one-shot. */
        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<WeatherRefreshWorker>()
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_NAME, ExistingWorkPolicy.REPLACE, request
            )
        }
    }
}
