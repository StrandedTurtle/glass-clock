package com.dylan.glasswidget.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.dylan.glasswidget.data.CalendarRefreshWorker
import com.dylan.glasswidget.data.WidgetDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Keeps the glass digits on time. They're images (a widget can't use a custom font), so unlike a
 * TextClock they don't tick by themselves: an exact alarm at every minute boundary redraws them.
 *
 * The alarm is RTC, not RTC_WAKEUP: with the screen off it never wakes the phone, it simply fires the
 * moment the device is awake again, so the cost is one tiny redraw per minute while you're using it.
 * Solid (system-font TextClock) widgets need none of this, and the ticker stops when none are glass.
 */
object ClockTicker {
    private const val ACTION_TICK = "com.dylan.glasswidget.CLOCK_TICK"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _minute = MutableStateFlow(currentMinute())
    /** Collected by the widget so a running Glance session recomposes on every tick. */
    val minute: StateFlow<Long> = _minute

    private fun currentMinute() = System.currentTimeMillis() / 60_000

    /** Redraw now and keep ticking — or stop, if no placed widget uses glass digits. */
    suspend fun tick(context: Context) {
        val app = context.applicationContext
        val widgets = placedSettings(app)
        if (widgets.any { it.clockStyle.usesImages }) {
            _minute.value = currentMinute()
            GlassClockWidget().updateAll(app)
            scheduleNext(app)
        } else {
            cancel(app)
        }
        // Ticks only happen while the phone is awake, so this keeps calendar links fresh while you're
        // using it without waking it otherwise. (Phone calendars update instantly via a change trigger.)
        if (widgets.any { it.showEvents && it.calendarLinks.isNotEmpty() }) {
            val fetched = WidgetDataStore.load(app).linksFetchedAtMs
            if (System.currentTimeMillis() - fetched > CalendarRefreshWorker.LINK_REFRESH_MS) {
                CalendarRefreshWorker.refreshLinksSoon(app)
            }
        }
    }

    /** Fire-and-forget [tick] for callers that aren't coroutines (receivers, activities). */
    fun refresh(context: Context, onDone: () -> Unit = {}) {
        val app = context.applicationContext
        scope.launch {
            try { tick(app) } finally { onDone() }
        }
    }

    private suspend fun placedSettings(context: Context): List<WidgetSettings> =
        GlanceAppWidgetManager(context).getGlanceIds(GlassClockWidget::class.java).map { id ->
            val prefs: Preferences = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
            WidgetSettings.from(prefs)
        }

    private fun scheduleNext(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val next = (currentMinute() + 1) * 60_000
        val pi = tickIntent(context)
        try {
            if (am.canScheduleExactAlarms()) am.setExact(AlarmManager.RTC, next, pi)
            else am.setWindow(AlarmManager.RTC, next, 10_000, pi)
        } catch (_: SecurityException) {
            am.setWindow(AlarmManager.RTC, next, 10_000, pi)
        }
    }

    private fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(tickIntent(context))
    }

    private fun tickIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ClockTickReceiver::class.java).setAction(ACTION_TICK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/**
 * Minute ticks, plus everything that can make the shown time wrong or kill the alarm chain:
 * clock/timezone changes, reboot and app updates.
 */
class ClockTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        ClockTicker.refresh(context) { pending.finish() }
    }
}
