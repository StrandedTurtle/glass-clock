package com.dylan.glasswidget.widget

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.dylan.glasswidget.R
import com.dylan.glasswidget.data.CalendarEvent
import com.dylan.glasswidget.data.CalendarRepository
import com.dylan.glasswidget.data.HourMode
import com.dylan.glasswidget.data.WeatherCondition
import com.dylan.glasswidget.data.WeatherSnapshot
import com.dylan.glasswidget.data.WidgetAlignment
import com.dylan.glasswidget.data.WidgetData
import com.dylan.glasswidget.data.WidgetDataStore
import com.dylan.glasswidget.data.WidgetSize
import com.dylan.glasswidget.data.WidgetText
import com.dylan.glasswidget.data.formatTemp
import java.time.ZoneId

class GlassClockWidget : GlanceAppWidget() {

    // Exact: lay out for the real size the launcher gives (one composition per orientation), so the
    // digits fill the space like the lockscreen clock and the settings preview matches pixel for pixel.
    // WidgetSize then picks how many pills fit from that height.
    override val sizeMode = SizeMode.Exact

    // Per-placed-widget settings live in Glance's own per-instance Preferences state.
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrDefault(0)
        val initial = WidgetDataStore.load(context)
        provideContent {
            // Collected, not read once, so a refresh landing while a session is alive still shows up.
            val data by WidgetDataStore.flow(context).collectAsState(initial)
            // Every minute tick recomposes, so the glass digits show the new time.
            val minute by ClockTicker.minute.collectAsState()
            val settings = WidgetSettings.from(currentState<Preferences>())
            GlassClockContent(context, appWidgetId, settings, data, minute)
        }
    }
}

/** Text and icon sizes per breakpoint, times the user's text-size setting. */
private data class Metrics(val pillSp: Float, val iconDp: Int, val gapDp: Float) {
    /** Rough rendered height of one pill, for sharing the height out (text + padding + rim). */
    val pillHeightDp: Float get() = pillSp * 1.3f + 16f

    /** Rough width of a detail item: icon, gap, then the text in the system font. */
    fun itemWidthDp(text: String): Float = (iconDp - 1) + 4f + text.length * pillSp * 0.56f

    companion object {
        fun of(size: WidgetSize, scale: Float): Metrics {
            val base = when (size) {
                WidgetSize.Compact -> Metrics(11f, 13, 4f)
                WidgetSize.Standard -> Metrics(13f, 15, 6f)
                WidgetSize.Tall -> Metrics(13.5f, 15, 6f)
                WidgetSize.Large -> Metrics(14f, 16, 7f)
            }
            return base.copy(pillSp = base.pillSp * scale, iconDp = (base.iconDp * scale).toInt())
        }
    }
}

private const val ITEM_GAP_DP = 12f
private const val PILL_PADDING_DP = 30f // 14dp each side + rim

@Suppress("UNUSED_PARAMETER") // [minute] is a recomposition key: each tick re-runs this with the new time
@Composable
private fun GlassClockContent(context: Context, appWidgetId: Int, s: WidgetSettings, data: WidgetData, minute: Long) {
    val widthDp = LocalSize.current.width.value - 8f   // minus the root padding
    val heightDp = LocalSize.current.height.value - 8f
    val size = WidgetSize.fromHeightDp(LocalSize.current.height.value)
    val m = Metrics.of(size, s.textScale)
    val palette = GlassPalette.of(context, s.tint, s.variant)

    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val use24h = when (s.hourMode) {
        HourMode.H24 -> true
        HourMode.H12 -> false
        HourMode.System -> DateFormat.is24HourFormat(context)
    }
    val clockText = WidgetText.clockText(now, zone, use24h, s.showColon)

    val location = s.location
    val weather: WeatherSnapshot? = location?.let { data.weather.entries[it.key] }
    val event: CalendarEvent? =
        if (s.showEvents) WidgetText.pickEvent(data.events, now, zone, s.eventsAllDay) else null

    // Resolved at render time; null means the zone is inert.
    val clockIntent = AppTargets.launchIntent(context, Zone.Clock, s.clockApp)
    val dateIntent = AppTargets.launchIntent(context, Zone.Date, s.dateApp)
    // Until a city is chosen, tapping the weather opens settings so it is never a dead end.
    val weatherIntent: Intent? =
        if (location == null) AppTargets.configIntent(context, appWidgetId)
        else AppTargets.launchIntent(context, Zone.Weather, s.weatherApp)

    // ---- Share the height out: pills first, the digits take what's left ------------------------
    val items = weather?.let { detailItems(context, it, s, now, zone, use24h) }.orEmpty()
    val maxPillContent = widthDp - PILL_PADDING_DP
    val packed = WidgetText.packRows(items.map { m.itemWidthDp(it.text) }, maxPillContent, ITEM_GAP_DP)
    // How many pills fit under the main one: Glance also caps the root column at 10 children.
    val extraRows = when (size) {
        WidgetSize.Compact, WidgetSize.Standard -> 0
        WidgetSize.Tall -> 2
        WidgetSize.Large -> 3
    }
    val showEvent = event != null && extraRows > 0
    var detailRows = packed.take(extraRows - if (showEvent) 1 else 0)
    val clockAspectWidth = WidgetText.clockWidth(clockText, 1f)
    fun digitHeight(rows: Int): Float {
        val pills = (1 + rows) * (m.pillHeightDp + m.gapDp)
        return minOf(heightDp - pills, widthDp / clockAspectWidth)
    }
    // Never let the pills squeeze the clock below ~40% of the widget.
    while (detailRows.isNotEmpty() && digitHeight(detailRows.size + if (showEvent) 1 else 0) < heightDp * 0.4f) {
        detailRows = detailRows.dropLast(1)
    }
    val eventRows = if (showEvent) 1 else 0
    val digitsDp = digitHeight(detailRows.size + eventRows).coerceAtLeast(24f)

    val hAlign = if (s.alignment == WidgetAlignment.Center) Alignment.CenterHorizontally else Alignment.Start
    val gap = m.gapDp.dp

    val digits: @Composable (GlanceModifier) -> Unit = { mod ->
        TapZone(clockIntent, mod) {
            ClockDigits(
                context, s.clockStyle, clockText, WidgetText.clock(now, zone, use24h),
                s.hourMode, s.showColon, s.alignment, palette, GlanceModifier.fillMaxSize(),
            )
        }
    }

    if (size == WidgetSize.Compact) {
        // 3×1: digits beside a two-line pill.
        val compactPill = 92f
        val compactDigits = minOf(heightDp, (widthDp - compactPill - 8f) / clockAspectWidth).coerceAtLeast(20f)
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = hAlign,
        ) {
            digits(GlanceModifier.width((compactDigits * clockAspectWidth).dp).height(compactDigits.dp))
            Spacer(GlanceModifier.width(8.dp))
            GlassPill(palette, compact = true) {
                Column {
                    TapZone(dateIntent) { DateText(context, s.datePreset, m.pillSp, palette) }
                    Spacer(GlanceModifier.height(3.dp))
                    TapZone(weatherIntent) { TempInline(context, s, palette, m, weather, location == null) }
                }
            }
        }
        return
    }

    // 4×2 and up: lockscreen stack — big glass digits, then floating pills.
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(4.dp),
        horizontalAlignment = hAlign,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        digits(GlanceModifier.fillMaxWidth().height(digitsDp.dp))
        Spacer(GlanceModifier.height(gap))
        MainPill(context, s, palette, m, weather, location == null, dateIntent, weatherIntent, roomForCondition = widthDp >= 290f)
        detailRows.forEach { row ->
            Spacer(GlanceModifier.height(gap))
            DetailPill(context, palette, m, row.map { items[it] }, weatherIntent)
        }
        if (showEvent && event != null) {
            Spacer(GlanceModifier.height(gap))
            EventPill(context, palette, m, event, now, zone, use24h)
        }
    }
}

/** Date | weather icon, temperature, condition and high/low. Two tap targets in one capsule. */
@Composable
private fun MainPill(
    context: Context,
    s: WidgetSettings,
    palette: GlassPalette,
    m: Metrics,
    weather: WeatherSnapshot?,
    noLocation: Boolean,
    dateIntent: Intent?,
    weatherIntent: Intent?,
    roomForCondition: Boolean,
) {
    GlassPill(palette) {
        TapZone(dateIntent) { DateText(context, s.datePreset, m.pillSp, palette) }
        PillDivider(palette)
        TapZone(weatherIntent) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TempInline(context, s, palette, m, weather, noLocation)
                val extras = weather?.let { pillExtras(context, it, s, roomForCondition) }.orEmpty()
                if (extras.isNotEmpty()) {
                    Spacer(GlanceModifier.width(7.dp))
                    LabelText(context, extras, m.pillSp, palette, LabelStyle.Secondary)
                }
            }
        }
    }
}

/** One row of detail items. Each item is a single child, so Glance's 10-per-row cap is never hit. */
@Composable
private fun DetailPill(context: Context, palette: GlassPalette, m: Metrics, row: List<DetailItem>, weatherIntent: Intent?) {
    TapZone(weatherIntent) {
        GlassPill(palette) {
            row.forEachIndexed { i, item ->
                if (i > 0) Spacer(GlanceModifier.width(ITEM_GAP_DP.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassIcon(item.icon, m.iconDp - 1, palette)
                    Spacer(GlanceModifier.width(4.dp))
                    LabelText(context, item.text, m.pillSp, palette)
                }
            }
        }
    }
}

@Composable
private fun EventPill(
    context: Context,
    palette: GlassPalette,
    m: Metrics,
    e: CalendarEvent,
    now: Long,
    zone: ZoneId,
    use24h: Boolean,
) {
    TapZone(CalendarRepository.viewIntent(e)) {
        GlassPill(palette) {
            GlassIcon(R.drawable.ic_d_event, m.iconDp - 1, palette)
            Spacer(GlanceModifier.width(6.dp))
            LabelText(
                context,
                WidgetText.eventWhen(
                    e, now, zone, use24h,
                    context.getString(R.string.event_now),
                    context.getString(R.string.event_today),
                    context.getString(R.string.event_tomorrow),
                ),
                m.pillSp, palette, LabelStyle.Secondary,
            )
            Spacer(GlanceModifier.width(6.dp))
            LabelText(context, e.title.ifBlank { context.getString(R.string.event_untitled) }, m.pillSp, palette)
        }
    }
}

/** Weather icon + "14°", or a prompt until the first fetch / until a city is set. */
@Composable
private fun TempInline(
    context: Context,
    s: WidgetSettings,
    palette: GlassPalette,
    m: Metrics,
    weather: WeatherSnapshot?,
    noLocation: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val condition = weather?.condition() ?: WeatherCondition.Unknown
        GlassIcon(condition.iconRes(), m.iconDp, palette, weather?.let { context.getString(condition.labelRes()) })
        Spacer(GlanceModifier.width(5.dp))
        LabelText(
            context,
            weather?.let { formatTemp(it.tempC, s.fahrenheit) }
                ?: context.getString(if (noLocation) R.string.set_location else R.string.temp_placeholder),
            m.pillSp, palette, LabelStyle.Emphasis,
        )
    }
}
