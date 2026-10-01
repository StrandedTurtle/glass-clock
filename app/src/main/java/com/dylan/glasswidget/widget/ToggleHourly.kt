package com.dylan.glasswidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

/**
 * Tapping the temperature flips the card between the usual items and the next few hours. The hourly
 * view flips back by itself after [SHOW_MS] (on the next minute redraw), or on another tap.
 */
class ToggleHourly : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val now = System.currentTimeMillis()
        updateAppWidgetState(context, glanceId) { p ->
            val showing = (p[WidgetPrefsKeys.HOURLY_UNTIL] ?: 0L) > now
            p[WidgetPrefsKeys.HOURLY_UNTIL] = if (showing) 0L else now + SHOW_MS
        }
        GlassClockWidget().update(context, glanceId)
    }

    companion object {
        const val SHOW_MS = 60_000L
    }
}
