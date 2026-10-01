package com.dylan.glasswidget.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width

/**
 * A floating glass capsule. The drawable's 999dp corner radius is clamped to half the height, so it's
 * a true pill at any size, with a top-lit sheen and a hairline rim. Real backdrop blur isn't available
 * to widgets, so the frost is translucency, like the digits.
 */
@Composable
fun GlassPill(
    palette: GlassPalette,
    modifier: GlanceModifier = GlanceModifier,
    compact: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .background(ImageProvider(palette.pillRes))
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 5.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * The same glass for several lines (date and weather, then more weather). With more than one line the
 * 999dp corners clamp to half the height, so it reads as a softly rounded glass card.
 */
@Composable
fun GlassCard(
    palette: GlassPalette,
    modifier: GlanceModifier = GlanceModifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .background(ImageProvider(palette.pillRes))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        content = content,
    )
}

/** Hairline separator between the date and the weather. A single child, so it's cheap in a row. */
@Composable
fun PillDivider(palette: GlassPalette) {
    Box(GlanceModifier.padding(horizontal = 10.dp)) {
        Box(GlanceModifier.width(1.dp).height(13.dp).background(palette.divider)) {}
    }
}
