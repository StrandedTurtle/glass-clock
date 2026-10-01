package com.dylan.glasswidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.dylan.glasswidget.data.CalendarRefreshWorker
import com.dylan.glasswidget.data.WeatherRefreshWorker

class GlassClockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GlassClockWidget()

    /** First widget placed: start the periodic refresh and fetch straight away. */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherRefreshWorker.schedule(context)
        WeatherRefreshWorker.refreshNow(context)
        CalendarRefreshWorker.observe(context)
        ClockTicker.refresh(context)
    }

    /** Cheap insurance that the periodic jobs and the minute ticker are running. */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WeatherRefreshWorker.schedule(context)
        CalendarRefreshWorker.observe(context)
        ClockTicker.refresh(context)
    }

    /** Last widget removed: the ticker notices there's nothing left to draw and stops. */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        ClockTicker.refresh(context)
    }
}
