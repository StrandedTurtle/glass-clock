package com.dylan.glasswidget.widget

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.appwidget.AppWidgetManager
import com.dylan.glasswidget.config.ConfigActivity

/** Which zone a tap target belongs to; decides the default app when the user hasn't picked one. */
enum class Zone { Clock, Date, Weather }

object AppTargets {

    /**
     * Resolved fresh on every render, so an app update or uninstall never leaves a dead shortcut.
     * [chosen]: null = use the zone's default, "" = deliberately nothing, else a package name.
     * Returns null when there is nothing to launch — the zone is then simply inert.
     */
    fun launchIntent(context: Context, zone: Zone, chosen: String?): Intent? {
        val pm = context.packageManager
        val pkg = when {
            chosen == null -> defaultPackage(pm, zone)
            chosen.isEmpty() -> null
            else -> chosen
        } ?: return null
        return runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull()
    }

    /** Package the system would use for this zone out of the box, or null if it can't tell. */
    fun defaultPackage(pm: PackageManager, zone: Zone): String? {
        val probe = when (zone) {
            Zone.Clock -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
            Zone.Date -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
            Zone.Weather -> return null
        }
        val pkg = runCatching {
            pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }.getOrNull()
        // "android" is the chooser, meaning no single default exists.
        return pkg?.takeIf { it != "android" }
    }

    /** Battery usage screen, for the charging countdown; null if the phone has none. */
    fun batteryIntent(context: Context): Intent? {
        val intent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent.takeIf { runCatching { context.packageManager.resolveActivity(it, 0) }.getOrNull() != null }
    }

    /** Opens the settings screen for one widget (used by the weather zone until a city is set). */
    fun configIntent(context: Context, appWidgetId: Int): Intent =
        Intent(context, ConfigActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            // Unique data so each widget gets its own PendingIntent rather than sharing one by extras.
            .setData(Uri.parse("glassclock://config/$appWidgetId"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
