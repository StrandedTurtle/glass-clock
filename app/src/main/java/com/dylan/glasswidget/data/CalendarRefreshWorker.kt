package com.dylan.glasswidget.data

import android.content.Context
import android.provider.CalendarContract
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dylan.glasswidget.widget.ClockTicker
import com.dylan.glasswidget.widget.GlassClockWidget
import java.time.Duration
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Re-reads the calendar and redraws. Runs (a) whenever the calendar database changes, via a
 * content-URI trigger, and (b) at the next moment the widget goes stale on its own — an event
 * starting/ending, sunrise/sunset, midnight — so "Now" and "Tomorrow" are never out of date.
 */
class CalendarRefreshWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        refreshAndRedraw(applicationContext)
        // A content trigger fires once; queue the next watcher behind this run.
        if (inputData.getBoolean(KEY_OBSERVER, false)) observe(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }

    companion object {
        private const val KEY_OBSERVER = "observer"
        private const val OBSERVER_NAME = "calendar_observer"
        private const val BOUNDARY_NAME = "widget_boundary"

        /** Shared by every refresh path: events -> cache -> redraw -> schedule the next boundary. */
        suspend fun refreshAndRedraw(context: Context) {
            WidgetDataStore.saveEvents(context, CalendarRepository.upcoming(context))
            GlassClockWidget().updateAll(context)
            // Insurance: re-arm the minute ticker in case the system killed its alarm chain.
            ClockTicker.tick(context)
            scheduleBoundary(context, WidgetDataStore.load(context))
        }

        /** Watch the calendar for changes. KEEP from the app; the worker itself appends its successor. */
        fun observe(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
            if (!CalendarRepository.hasPermission(context)) return
            val request = OneTimeWorkRequestBuilder<CalendarRefreshWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
                        .setTriggerContentUpdateDelay(Duration.ofSeconds(5))
                        .setTriggerContentMaxDelay(Duration.ofMinutes(1))
                        .build()
                )
                .setInputData(workDataOf(KEY_OBSERVER to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(OBSERVER_NAME, policy, request)
        }

        /** One-shot redraw at the next time the content changes by itself. Must be a caller's last step. */
        private fun scheduleBoundary(context: Context, data: WidgetData) {
            val now = System.currentTimeMillis()
            val sun = data.weather.entries.values.flatMap { it.sunEvents }
            val next = WidgetText.nextBoundary(data.events, sun, now, ZoneId.systemDefault()) ?: return
            val delay = (next - now + 15_000).coerceAtLeast(60_000)
            val request = OneTimeWorkRequestBuilder<CalendarRefreshWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(BOUNDARY_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
