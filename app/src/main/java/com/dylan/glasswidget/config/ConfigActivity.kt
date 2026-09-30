package com.dylan.glasswidget.config

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.glance.appwidget.GlanceAppWidgetManager

/**
 * Launched by the home screen when a widget is added (and from "reconfigure"), and by [MainActivity].
 * Backing out leaves the result CANCELED, which makes the launcher discard a widget that was just added.
 */
class ConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(Activity.RESULT_CANCELED)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Also refuse an id Glance doesn't know about, rather than crashing on it.
        val known = appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID &&
            runCatching { GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId) }.isSuccess
        if (!known) { finish(); return }

        setContent {
            AppTheme {
                ConfigScreen(appWidgetId = appWidgetId) {
                    setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                    finish()
                }
            }
        }
    }
}
