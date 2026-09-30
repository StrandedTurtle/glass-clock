package com.dylan.glasswidget.widget

import android.content.Context
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.AndroidRemoteViews
import com.dylan.glasswidget.R
import com.dylan.glasswidget.data.DatePreset
import com.dylan.glasswidget.data.HourMode

/**
 * All widget text is a plain Android TextView/TextClock wrapped in Glance's AndroidRemoteViews, because
 * Glance's own Text can't set a font. As a bonus the system keeps TextClock ticking without our process.
 */

private fun RemoteViews.style(id: Int, sizeSp: Float, palette: GlassPalette, secondary: Boolean) {
    setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sizeSp)
    // Day/night pair (API 31): the launcher swaps them itself when the system theme changes.
    setColorInt(
        id, "setTextColor",
        if (secondary) palette.secondaryDay else palette.textDay,
        if (secondary) palette.secondaryNight else palette.textNight,
    )
}

@Composable
fun ClockText(
    context: Context,
    hourMode: HourMode,
    sizeSp: Float,
    palette: GlassPalette,
    modifier: GlanceModifier = GlanceModifier,
) {
    val views = RemoteViews(context.packageName, R.layout.textclock_time).apply {
        when (hourMode) {
            HourMode.System -> Unit // layout's own 12h/24h formats follow the phone setting
            HourMode.H12 -> {
                setCharSequence(R.id.clockText, "setFormat12Hour", "h:mm")
                setCharSequence(R.id.clockText, "setFormat24Hour", "h:mm")
            }
            HourMode.H24 -> {
                setCharSequence(R.id.clockText, "setFormat12Hour", "HH:mm")
                setCharSequence(R.id.clockText, "setFormat24Hour", "HH:mm")
            }
        }
        style(R.id.clockText, sizeSp, palette, secondary = false)
    }
    AndroidRemoteViews(views, modifier)
}

@Composable
fun DateText(
    context: Context,
    preset: DatePreset,
    sizeSp: Float,
    palette: GlassPalette,
    modifier: GlanceModifier = GlanceModifier,
) {
    val views = RemoteViews(context.packageName, R.layout.textclock_date).apply {
        setCharSequence(R.id.dateText, "setFormat12Hour", preset.pattern)
        setCharSequence(R.id.dateText, "setFormat24Hour", preset.pattern)
        style(R.id.dateText, sizeSp, palette, secondary = true)
    }
    AndroidRemoteViews(views, modifier)
}

@Composable
fun LabelText(
    context: Context,
    text: String,
    sizeSp: Float,
    palette: GlassPalette,
    secondary: Boolean = false,
    modifier: GlanceModifier = GlanceModifier,
) {
    val views = RemoteViews(context.packageName, R.layout.label_text).apply {
        setTextViewText(R.id.labelText, text)
        style(R.id.labelText, sizeSp, palette, secondary)
    }
    AndroidRemoteViews(views, modifier)
}
