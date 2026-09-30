package com.dylan.glasswidget.config

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Shortcuts to the settings that decide whether background refresh survives on aggressive OEM ROMs. */
object Reliability {

    fun openBatterySettings(context: Context) {
        if (!tryStart(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) openAppInfo(context)
    }

    /** HyperOS/MIUI Autostart list; falls back to the app's info page on other devices. */
    fun openAutostart(context: Context) {
        val miui = Intent().setComponent(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
        )
        if (!tryStart(context, miui)) openAppInfo(context)
    }

    fun openAppInfo(context: Context) {
        tryStart(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)
}
