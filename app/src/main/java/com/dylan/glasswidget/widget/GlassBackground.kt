package com.dylan.glasswidget.widget

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import com.dylan.glasswidget.R
import com.dylan.glasswidget.data.GlassVariant
import com.dylan.glasswidget.data.Limits

/**
 * The "soft light glass" panel, in two layers:
 *   1. a translucent, theme-tinted scrim clipped to the corner radius, and
 *   2. a hairline edge + faint diagonal sheen drawn on top (a drawable per supported radius,
 *      because RemoteViews can't stroke a border whose radius is chosen at runtime).
 * Real backdrop blur isn't possible for a widget, so this is deliberately all translucency.
 */
@Composable
fun GlassBackground(
    palette: GlassPalette,
    variant: GlassVariant,
    cornerRadiusDp: Int,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(cornerRadiusDp.dp)
            .background(palette.scrim),
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(glassEdgeRes(variant, cornerRadiusDp))),
        ) {
            content()
        }
    }
}

private fun glassEdgeRes(variant: GlassVariant, radiusDp: Int): Int {
    val clear = variant == GlassVariant.Clear
    return when (Limits.snapRadius(radiusDp)) {
        16 -> if (clear) R.drawable.glass_edge_clear_16 else R.drawable.glass_edge_soft_16
        20 -> if (clear) R.drawable.glass_edge_clear_20 else R.drawable.glass_edge_soft_20
        24 -> if (clear) R.drawable.glass_edge_clear_24 else R.drawable.glass_edge_soft_24
        28 -> if (clear) R.drawable.glass_edge_clear_28 else R.drawable.glass_edge_soft_28
        32 -> if (clear) R.drawable.glass_edge_clear_32 else R.drawable.glass_edge_soft_32
        else -> if (clear) R.drawable.glass_edge_clear_36 else R.drawable.glass_edge_soft_36
    }
}
