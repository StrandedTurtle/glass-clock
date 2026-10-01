package com.dylan.glasswidget.widget

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Typeface
import android.os.BatteryManager
import android.provider.AlarmClock
import android.util.TypedValue
import com.dylan.glasswidget.data.SmartLine

/**
 * The next alarm, but only a real one. Android's "next alarm clock" is whatever any app last set with
 * setAlarmClock, which includes calendar, reminder and bedtime apps, so an alarm only counts when it
 * was set by the phone's clock app or an app that's clearly an alarm clock.
 */
object Alarms {
    data class Next(val atMs: Long, val creator: String?, val counts: Boolean)

    // Clock apps whose package names don't say "clock" or "alarm".
    private val KNOWN = setOf(
        "com.urbandroid.sleep",        // Sleep as Android
        "droom.sleepIfUCan",           // Alarmy
        "com.androidrocker.voicealarm",
    )

    fun next(context: Context): Next? {
        val info = runCatching { context.getSystemService(AlarmManager::class.java)?.nextAlarmClock }.getOrNull() ?: return null
        val creator = info.showIntent?.creatorPackage
        return Next(info.triggerTime, creator, creator != null && isClockApp(context, creator))
    }

    /** Trigger time of the next alarm that [next] counts, or null. */
    fun nextClockAlarmMs(context: Context): Long? = next(context)?.takeIf { it.counts }?.atMs

    private fun isClockApp(context: Context, pkg: String): Boolean {
        if (pkg in KNOWN) return true
        val lower = pkg.lowercase()
        if ("clock" in lower || "alarm" in lower) return true
        return defaultClockPackage(context.packageManager) == pkg
    }

    private fun defaultClockPackage(pm: PackageManager): String? = runCatching {
        pm.resolveActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }.getOrNull()

    /** The app's name for settings ("Clock", "Google Calendar"), or the package if it can't be read. */
    fun appName(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}

object Battery {
    private const val PREFS = "battery_session"

    /**
     * Level, Android's time-to-full and the measured charge rate while plugged in; null otherwise.
     * "Plugged in" comes from the power source, not "is charging", so a phone held at a charge limit
     * (or paused by adaptive charging) still counts.
     */
    fun charging(context: Context): SmartLine.Charging? {
        val app = context.applicationContext
        val status = runCatching {
            app.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val plugged = (status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!plugged) {
            if (prefs.contains("start_ms")) prefs.edit().clear().apply()
            return null
        }
        val bm = app.getSystemService(BatteryManager::class.java)
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
            ?: status?.let { s ->
                val l = s.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = s.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (l >= 0 && scale > 0) l * 100 / scale else null
            } ?: return null
        val full = runCatching { bm?.computeChargeTimeRemaining() }.getOrNull()?.takeIf { it > 0 }

        // Measure the rate ourselves: Android often has no estimate on wireless pads or with a limit.
        val now = System.currentTimeMillis()
        val startMs = prefs.getLong("start_ms", 0L)
        val startLevel = prefs.getInt("start_level", -1)
        if (startMs == 0L || startLevel < 0 || level < startLevel) {
            prefs.edit().putLong("start_ms", now).putInt("start_level", level).apply()
            return SmartLine.Charging(level, full)
        }
        val hours = (now - startMs) / 3_600_000.0
        // Wait for a couple of points (and a few minutes) so the first percent's timing doesn't skew it.
        val rate = if (level - startLevel >= 2 && hours > 0.05) (level - startLevel) / hours else null
        return SmartLine.Charging(level, full, rate)
    }
}

/**
 * Widths of widget text, measured in the system font the launcher draws it in, so the card knows what
 * really fits on a line instead of guessing (a guess too small clips the last item).
 */
class TextWidths(context: Context) {
    private val metrics = context.resources.displayMetrics
    private val regular = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
    private val medium = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }

    /** Width in dp of [text] at [sp], with a little slack for rounding and font differences. */
    fun dp(text: String, sp: Float, bold: Boolean = false): Float {
        val paint = if (bold) medium else regular
        // applyDimension follows the user's font size, including Android 14's non-linear scaling.
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics)
        return paint.measureText(text) / metrics.density * 1.04f + 2f
    }
}
