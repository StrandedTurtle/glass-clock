package com.dylan.glasswidget.config

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.glance.appwidget.GlanceAppWidgetManager

/**
 * Launched by the home screen when a widget is added (and from "reconfigure"), and by [MainActivity].
 * Uses a wallpaper-showing window theme so the live preview sits on your actual home-screen wallpaper.
 * Backing out leaves the result CANCELED, which makes the launcher discard a widget that was just added.
 */
class ConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The top of this screen is the real wallpaper, so keep status-bar icons light.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
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
