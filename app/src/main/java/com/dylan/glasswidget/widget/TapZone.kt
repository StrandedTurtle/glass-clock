package com.dylan.glasswidget.widget

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box

/**
 * A single-tap zone. With no [intent] (nothing chosen, or the app was uninstalled) no click is
 * attached at all, so the zone is inert rather than broken. Single tap only — Niagara owns long-press.
 */
@Composable
fun TapZone(
    intent: Intent?,
    modifier: GlanceModifier = GlanceModifier,
    contentAlignment: Alignment = Alignment.CenterStart,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = if (intent != null) modifier.clickable(actionStartActivity(intent)) else modifier,
        contentAlignment = contentAlignment,
        content = content,
    )
}
