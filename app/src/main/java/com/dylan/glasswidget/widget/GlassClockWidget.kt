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
import androidx.glance.layout.fillMaxHeight
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
import com.dylan.glasswidget.data.WeatherDetail
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
    // WidgetSize then picks one of four layouts from that height.
    override val sizeMode = SizeMode.Exact

    // Per-placed-widget settings live in Glance's own per-instance Preferences state.
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrDefault(0)
        val initial = WidgetDataStore.load(context)
        provideContent {
            // Collected, not read once, so a refresh landing while a session is alive still shows up.
            val data by WidgetDataStore.flow(context).collectAsState(initial)
            val settings = WidgetSettings.from(currentState<Preferences>())
            GlassClockContent(context, appWidgetId, settings, data)
        }
    }
}

/** Text and icon sizes per breakpoint, times the user's text-size setting. */
private data class Metrics(val pillSp: Float, val iconDp: Int, val topSp: Float, val gapDp: Int) {
    companion object {
        fun of(size: WidgetSize, scale: Float): Metrics {
            val base = when (size) {
                WidgetSize.Compact -> Metrics(11f, 13, 11f, 4)
                WidgetSize.Standard -> Metrics(13f, 15, 12.5f, 6)
                WidgetSize.Tall -> Metrics(13.5f, 15, 13f, 6)
                WidgetSize.Large -> Metrics(14f, 16, 13.5f, 8)
            }
            return base.copy(pillSp = base.pillSp * scale, iconDp = (base.iconDp * scale).toInt(), topSp = base.topSp * scale)
        }
    }
}

@Composable
private fun GlassClockContent(context: Context, appWidgetId: Int, s: WidgetSettings, data: WidgetData) {
    val size = WidgetSize.fromHeightDp(LocalSize.current.height.value)
    val widthDp = LocalSize.current.width.value
    val m = Metrics.of(size, s.textScale)
    val palette = GlassPalette.of(context, s.tint, s.variant)

    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val use24h = when (s.hourMode) {
        HourMode.H24 -> true
        HourMode.H12 -> false
        HourMode.System -> DateFormat.is24HourFormat(context)
    }

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

    val hAlign = if (s.alignment == WidgetAlignment.Center) Alignment.CenterHorizontally else Alignment.Start

    val digits: @Composable (GlanceModifier) -> Unit = { mod ->
        TapZone(clockIntent, mod) {
            GlassDigits(context, s.clockStyle, s.hourMode, s.showColon, s.alignment, palette, GlanceModifier.fillMaxSize())
        }
    }
    val mainPill: @Composable () -> Unit = {
        MainPill(context, s, palette, m, weather, location == null, dateIntent, weatherIntent, roomForCondition = widthDp >= 300f)
    }
    val details = weather?.let { detailItems(context, it, s) }.orEmpty()
    val detailsPill: @Composable () -> Unit = {
        TapZone(weatherIntent) {
            GlassPill(palette) {
                details.take(4).forEachIndexed { i, item ->
                    if (i > 0) Spacer(GlanceModifier.width(12.dp))
                    GlassIcon(item.icon, m.iconDp - 1, palette)
                    Spacer(GlanceModifier.width(4.dp))
                    LabelText(context, item.text, m.pillSp - 0.5f, palette)
                }
            }
        }
    }
    val eventPill: @Composable (CalendarEvent) -> Unit = { e ->
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
                    m.pillSp - 0.5f, palette, LabelStyle.Secondary,
                )
                Spacer(GlanceModifier.width(6.dp))
                LabelText(context, e.title.ifBlank { context.getString(R.string.event_untitled) }, m.pillSp - 0.5f, palette)
            }
        }
    }
    val sun = weather?.takeIf { WeatherDetail.SunTimes in s.details }?.let { WidgetText.nextSunEvent(it.sunEvents, now) }
    val topLine: @Composable () -> Unit = {
        if (sun != null) {
            TapZone(weatherIntent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassIcon(if (sun.sunrise) R.drawable.ic_d_sunrise else R.drawable.ic_d_sunset, m.iconDp - 1, palette)
                    Spacer(GlanceModifier.width(5.dp))
                    LabelText(
                        context,
                        context.getString(
                            if (sun.sunrise) R.string.sunrise_at else R.string.sunset_at,
                            WidgetText.clock(sun.atEpochMs, zone, use24h),
                        ),
                        m.topSp, palette, LabelStyle.OnWallpaper,
                    )
                }
            }
        }
    }
    val gap = m.gapDp.dp

    when (size) {
        // 3×1: digits beside a two-line pill.
        WidgetSize.Compact -> Row(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            digits(GlanceModifier.defaultWeight().fillMaxHeight())
            Spacer(GlanceModifier.width(8.dp))
            GlassPill(palette, compact = true) {
                Column {
                    TapZone(dateIntent) { DateText(context, s.datePreset, m.pillSp, palette) }
                    Spacer(GlanceModifier.height(3.dp))
                    TapZone(weatherIntent) { TempInline(context, s, palette, m, weather, location == null) }
                }
            }
        }

        // 4×2 and up: lockscreen stack — sun line, big glass digits, then floating pills.
        else -> Column(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalAlignment = hAlign,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (size != WidgetSize.Standard && sun != null) {
                topLine()
                Spacer(GlanceModifier.height(4.dp))
            }
            digits(GlanceModifier.fillMaxWidth().defaultWeight())
            Spacer(GlanceModifier.height(gap))
            mainPill()
            when (size) {
                WidgetSize.Tall -> {
                    // One extra pill: your next event when there is one, else the weather details.
                    if (event != null) { Spacer(GlanceModifier.height(gap)); eventPill(event) }
                    else if (details.isNotEmpty()) { Spacer(GlanceModifier.height(gap)); detailsPill() }
                }
                WidgetSize.Large -> {
                    if (details.isNotEmpty()) { Spacer(GlanceModifier.height(gap)); detailsPill() }
                    if (event != null) { Spacer(GlanceModifier.height(gap)); eventPill(event) }
                }
                else -> Unit
            }
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
