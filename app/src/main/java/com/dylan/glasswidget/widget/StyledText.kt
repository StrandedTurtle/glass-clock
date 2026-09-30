package com.dylan.glasswidget.widget

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.AndroidRemoteViews
import com.dylan.glasswidget.R
import com.dylan.glasswidget.data.ClockStyle
import com.dylan.glasswidget.data.DatePreset
import com.dylan.glasswidget.data.HourMode
import com.dylan.glasswidget.data.WidgetAlignment

/**
 * All widget text is a real TextView/TextClock wrapped in Glance's AndroidRemoteViews: Glance's own
 * Text can't take a font, and the system keeps a TextClock ticking without our process running.
 */

private fun RemoteViews.color(id: Int, day: Int, night: Int) {
    // Day/night pair (API 31+): the launcher swaps them itself when the system theme changes.
    setColorInt(id, "setTextColor", day, night)
}

/** 12h/24h patterns with or without the colon; HyperOS's lockscreen clock has none. */
internal fun clockFormats(hourMode: HourMode, colon: Boolean): Pair<String, String> {
    val sep = if (colon) ":" else ""
    val h12 = "h${sep}mm"
    val h24 = "HH${sep}mm"
    return when (hourMode) {
        HourMode.System -> h12 to h24
        HourMode.H12 -> h12 to h12
        HourMode.H24 -> h24 to h24
    }
}

/** The big glass digits. They auto-size to fill [modifier]'s box, so give it a fixed height. */
@Composable
fun GlassDigits(
    context: Context,
    style: ClockStyle,
    hourMode: HourMode,
    colon: Boolean,
    alignment: WidgetAlignment,
    palette: GlassPalette,
    modifier: GlanceModifier,
) {
    val layout = if (style == ClockStyle.Glass) R.layout.textclock_glass else R.layout.textclock_solid
    val (f12, f24) = clockFormats(hourMode, colon)
    val views = RemoteViews(context.packageName, layout).apply {
        setCharSequence(R.id.clockText, "setFormat12Hour", f12)
        setCharSequence(R.id.clockText, "setFormat24Hour", f24)
        setInt(
            R.id.clockText, "setGravity",
            if (alignment == WidgetAlignment.Center) Gravity.CENTER else Gravity.START or Gravity.CENTER_VERTICAL,
        )
        color(R.id.clockText, palette.digitDay, palette.digitNight)
    }
    AndroidRemoteViews(views, modifier)
}

@Composable
fun DateText(context: Context, preset: DatePreset, sizeSp: Float, palette: GlassPalette) {
    val views = RemoteViews(context.packageName, R.layout.textclock_date).apply {
        setCharSequence(R.id.dateText, "setFormat12Hour", preset.pattern)
        setCharSequence(R.id.dateText, "setFormat24Hour", preset.pattern)
        setTextViewTextSize(R.id.dateText, TypedValue.COMPLEX_UNIT_SP, sizeSp)
        color(R.id.dateText, palette.textDay, palette.textNight)
    }
    AndroidRemoteViews(views)
}

enum class LabelStyle { Normal, Secondary, Emphasis, OnWallpaper }

@Composable
fun LabelText(
    context: Context,
    text: String,
    sizeSp: Float,
    palette: GlassPalette,
    style: LabelStyle = LabelStyle.Normal,
) {
    val layout = when (style) {
        LabelStyle.Emphasis -> R.layout.label_text_medium
        LabelStyle.OnWallpaper -> if (palette.lightText) R.layout.label_text_shadow else R.layout.label_text
        else -> R.layout.label_text
    }
    val views = RemoteViews(context.packageName, layout).apply {
        setTextViewText(R.id.labelText, text)
        setTextViewTextSize(R.id.labelText, TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (style == LabelStyle.Secondary) color(R.id.labelText, palette.secondaryDay, palette.secondaryNight)
        else color(R.id.labelText, palette.textDay, palette.textNight)
    }
    AndroidRemoteViews(views)
}
