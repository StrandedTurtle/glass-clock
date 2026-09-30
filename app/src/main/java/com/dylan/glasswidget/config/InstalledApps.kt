package com.dylan.glasswidget.config

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable

data class AppInfo(val label: String, val packageName: String, val icon: Drawable)

object InstalledApps {

    /** Every app with a launcher entry, one row per package, sorted by name. Needs the <queries> block. */
    fun launchable(pm: PackageManager): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val found: List<ResolveInfo> =
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        return found
            .distinctBy { it.activityInfo.packageName }
            .map {
                AppInfo(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    icon = it.loadIcon(pm),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    /** Human label for a package, or the package name if it's gone (uninstalled). */
    fun labelFor(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0)).loadLabel(pm).toString()
    }.getOrDefault(packageName)
}
