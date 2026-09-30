package com.dylan.glasswidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.dylan.glasswidget.data.WeatherRefreshWorker

class GlassClockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GlassClockWidget()

    /** First widget placed: start the periodic refresh and fetch straight away. */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherRefreshWorker.schedule(context)
        WeatherRefreshWorker.refreshNow(context)
    }

    /** Cheap insurance that the periodic job exists (KEEP makes this a no-op when it does). */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WeatherRefreshWorker.schedule(context)
    }
}
