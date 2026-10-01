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
import com.dylan.glasswidget.data.HourForecast
import com.dylan.glasswidget.data.RainOutlook
import com.dylan.glasswidget.data.SmartLine
import com.dylan.glasswidget.data.WarningLevel
import com.dylan.glasswidget.data.WeatherDetail
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
    /** Rough rendered height of one line of pill text, and of a one-line pill (text + padding + rim). */
    val lineHeightDp: Float get() = maxOf(pillSp * 1.3f, iconDp.toFloat())
    val pillHeightDp: Float get() = lineHeightDp + 16f

    /** Rough height of the hourly strip: hour label, icon, temperature. */
    val hourlyHeightDp: Float get() = (pillSp - 1.5f) * 1.3f + 2f + iconDp + 2f + (pillSp - 0.5f) * 1.3f

    /** Width of a detail item as [DetailChip] draws it: optional icon and gap, then its text. */
    fun itemWidthDp(item: DetailItem, widths: TextWidths): Float =
        (if (item.icon != null) iconDp - 1f + 4f else 0f) + widths.dp(item.text, pillSp)

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
private const val LINE_GAP_DP = 5f
private const val EVENT_GAP_DP = 4f
private const val HOUR_SLOT_DP = 44f
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
        if (s.showEvents) WidgetText.pickEvent(s.visibleEvents(data.events), now, zone, s.eventsAllDay) else null

    // Resolved at render time; null means the zone is inert.
    val clockIntent = AppTargets.launchIntent(context, Zone.Clock, s.clockApp)
    val dateIntent = AppTargets.launchIntent(context, Zone.Date, s.dateApp)
    // Until a city is chosen, tapping the weather opens settings so it is never a dead end.
    val weatherIntent: Intent? =
        if (location == null) AppTargets.configIntent(context, appWidgetId)
        else AppTargets.launchIntent(context, Zone.Weather, s.weatherApp)

    // ---- Share the height out: the glass card first, the digits take what's left -------------
    val widths = TextWidths(context)
    val items = weather?.let { detailItems(context, it, s, now, zone, use24h) }.orEmpty()
    val maxLine = widthDp - PILL_PADDING_DP
    // Line 1 already holds "date | icon 14°"; details follow on it while they fit, then wrap.
    val dateText = java.text.SimpleDateFormat(s.datePreset.pattern, java.util.Locale.getDefault()).format(java.util.Date(now))
    val tempText = weather?.let { formatTemp(it.tempC, s.fahrenheit) }
        ?: context.getString(if (location == null) R.string.set_location else R.string.temp_placeholder)
    val firstLineUsed = widths.dp(dateText, m.pillSp) + 21f + m.iconDp + 5f + widths.dp(tempText, m.pillSp, bold = true)
    val itemWidths = items.map { m.itemWidthDp(it, widths) }
    fun pack(maxRows: Int) = WidgetText.packRows(
        itemWidths, maxLine, ITEM_GAP_DP, perRow = 5, firstRowUsed = firstLineUsed, firstRowMax = 4, maxRows = maxRows,
    )
    // Lines the card may use: the user's choice, or by widget height on Auto.
    val autoLines = s.cardLines.lines == null
    val maxLines = s.cardLines.lines ?: when (size) {
        WidgetSize.Compact -> 1
        WidgetSize.Standard -> 2
        WidgetSize.Tall -> 3
        WidgetSize.Large -> 4
    }
    // ---- Smart line above the clock: the most useful thing right now (like the lockscreen top line) ----
    val smartItems = SmartLine.items(
        now, zone, use24h, event,
        alarmMs = if (s.smartAlarm) Alarms.nextClockAlarmMs(context) else null,
        rain = weather?.let { RainOutlook.from(it.rainSlots, now) },
        warnings = weather?.warnings.orEmpty(),
        sources = SmartLine.Sources(
            events = s.showEvents, rain = s.smartRain, alarm = s.smartAlarm, warnings = s.smartWarnings,
            charge = s.smartCharge, health = s.smartHealth, sun = s.smartSun, frost = s.smartFrost,
        ),
        labels = smartLabels(context, s.fahrenheit),
        weather = weather,
        charging = if (s.smartCharge != SmartLine.ChargeTarget.Off) Battery.charging(context) else null,
        usAqi = s.fahrenheit,
    )
    val smart = pickSmart(smartItems, widthDp, m.pillSp, widths)
    var showSmart = smart.isNotEmpty() && size != WidgetSize.Compact

    // ---- Hourly strip inside the card ----
    val hourSlots = (maxLine / HOUR_SLOT_DP).toInt().coerceIn(3, 6)
    val hours = if (WeatherDetail.Hourly in s.details) weather?.hourly.orEmpty().take(hourSlots) else emptyList()
    var showHourly = hours.size >= 3 && size != WidgetSize.Compact

    // When space is short, items at the end of the user's list give way first (a whole line at a time),
    // then the hourly strip; the smart line only goes if the clock would get tiny. A fixed line count is
    // honoured unless the clock would shrink below a quarter of the height.
    var rowLimit = maxLines.coerceAtLeast(1)
    var lines = pack(rowLimit)
    val clockAspectWidth = WidgetText.clockWidth(clockText, 1f, s.clockFace)
    fun digitHeight(): Float {
        val hourly = if (showHourly) m.hourlyHeightDp + LINE_GAP_DP else 0f
        val card = lines.size * m.lineHeightDp + (lines.size - 1) * LINE_GAP_DP + hourly + 16f
        val smartLine = if (showSmart) m.lineHeightDp + EVENT_GAP_DP else 0f
        return minOf(heightDp - card - m.gapDp - smartLine, widthDp / clockAspectWidth)
    }
    val minShare = if (autoLines) 0.4f else 0.25f
    while (rowLimit > 1 && lines.size > 1 && digitHeight() < heightDp * minShare) {
        rowLimit = lines.size - 1
        lines = pack(rowLimit)
    }
    if (showHourly && digitHeight() < heightDp * minShare) showHourly = false
    if (showSmart && digitHeight() < heightDp * 0.3f) showSmart = false
    val digitsDp = (digitHeight() * s.clockScale).coerceAtLeast(24f)

    val hAlign = if (s.alignment == WidgetAlignment.Center) Alignment.CenterHorizontally else Alignment.Start
    val gap = m.gapDp.dp

    val digits: @Composable (GlanceModifier) -> Unit = { mod ->
        TapZone(clockIntent, mod) {
            ClockDigits(
                context, s.clockStyle, s.clockFace, clockText, WidgetText.clock(now, zone, use24h),
                s.hourMode, s.showColon, s.alignment, palette, GlanceModifier.fillMaxSize(),
            )
        }
    }

    if (size == WidgetSize.Compact) {
        // 3×1: digits beside a two-line card.
        val compactPill = 92f
        val compactDigits = (minOf(heightDp, (widthDp - compactPill - 8f) / clockAspectWidth) * s.clockScale)
            .coerceAtLeast(20f)
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = hAlign,
        ) {
            digits(GlanceModifier.width((compactDigits * clockAspectWidth).dp).height(compactDigits.dp))
            Spacer(GlanceModifier.width(8.dp))
            GlassCard(palette) {
                TapZone(dateIntent) { DateText(context, s.datePreset, m.pillSp, palette) }
                Spacer(GlanceModifier.height(3.dp))
                TapZone(weatherIntent) { TempInline(context, s, palette, m, weather, location == null) }
            }
        }
        return
    }

    // 4×2 and up: lockscreen stack — big glass digits, then the floating glass card.
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(4.dp),
        horizontalAlignment = hAlign,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showSmart) {
            // Tapping goes where the first item points: the event (or Maps if it has a location),
            // the weather app for rain and warnings, the clock app for the alarm.
            val first = smart.first()
            val intent = when (first.kind) {
                SmartLine.Kind.Event -> event?.let { e ->
                    e.location?.let(CalendarRepository::mapsIntent)
                        ?: if (e.fromLink) dateIntent else CalendarRepository.viewIntent(e)
                }
                SmartLine.Kind.Rain, SmartLine.Kind.Warning, SmartLine.Kind.Health,
                SmartLine.Kind.Frost, SmartLine.Kind.Sun -> weatherIntent
                SmartLine.Kind.Alarm -> clockIntent
                SmartLine.Kind.Charging -> AppTargets.batteryIntent(context)
            }
            TapZone(intent) {
                LabelText(context, smart.joinToString(" • ") { it.text }, m.pillSp, palette, LabelStyle.OnWallpaper)
            }
            Spacer(GlanceModifier.height(EVENT_GAP_DP.dp))
        }
        digits(GlanceModifier.fillMaxWidth().height(digitsDp.dp))
        Spacer(GlanceModifier.height(gap))
        InfoCard(
            context, s, palette, m, weather, location == null, dateIntent, weatherIntent,
            lines.map { row -> row.map { items[it] } },
            hours = if (showHourly) hours else emptyList(), use24h = use24h, zone = zone,
        )
    }
}

/**
 * One glass card: "date | icon temperature" then the chosen details, continuing on the first line while
 * they fit, then any details that wrapped onto further lines. Date and weather are separate taps.
 */
@Composable
private fun InfoCard(
    context: Context,
    s: WidgetSettings,
    palette: GlassPalette,
    m: Metrics,
    weather: WeatherSnapshot?,
    noLocation: Boolean,
    dateIntent: Intent?,
    weatherIntent: Intent?,
    lines: List<List<DetailItem>>,
    hours: List<HourForecast>,
    use24h: Boolean,
    zone: ZoneId,
) {
    GlassCard(palette) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TapZone(dateIntent) { DateText(context, s.datePreset, m.pillSp, palette) }
            PillDivider(palette)
            TapZone(weatherIntent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TempInline(context, s, palette, m, weather, noLocation)
                    lines.firstOrNull().orEmpty().forEach { item ->
                        Spacer(GlanceModifier.width(ITEM_GAP_DP.dp))
                        DetailChip(context, palette, m, item)
                    }
                }
            }
        }
        if (hours.isNotEmpty()) {
            Spacer(GlanceModifier.height(LINE_GAP_DP.dp))
            TapZone(weatherIntent) { HourlyStrip(context, palette, m, hours, s, use24h, zone) }
        }
        lines.drop(1).forEach { line ->
            Spacer(GlanceModifier.height(LINE_GAP_DP.dp))
            TapZone(weatherIntent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    line.forEachIndexed { i, item ->
                        if (i > 0) Spacer(GlanceModifier.width(ITEM_GAP_DP.dp))
                        DetailChip(context, palette, m, item)
                    }
                }
            }
        }
    }
}

/** Icon (if any) and text as a single child, so Glance's 10-per-row cap is never hit. */
@Composable
private fun DetailChip(context: Context, palette: GlassPalette, m: Metrics, item: DetailItem) {
    if (item.icon == null) {
        LabelText(context, item.text, m.pillSp, palette, LabelStyle.Secondary)
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIcon(item.icon, m.iconDp - 1, palette)
            Spacer(GlanceModifier.width(4.dp))
            LabelText(context, item.text, m.pillSp, palette)
        }
    }
}

/** The next few hours: time, icon and temperature in columns. One child per hour (Glance caps rows at 10). */
@Composable
private fun HourlyStrip(
    context: Context,
    palette: GlassPalette,
    m: Metrics,
    hours: List<HourForecast>,
    s: WidgetSettings,
    use24h: Boolean,
    zone: ZoneId,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        hours.forEach { h ->
            Column(
                modifier = GlanceModifier.width(HOUR_SLOT_DP.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LabelText(context, WidgetText.hourLabel(h.atEpochMs, zone, use24h), m.pillSp - 1.5f, palette, LabelStyle.Secondary)
                Spacer(GlanceModifier.height(2.dp))
                GlassIcon(WeatherCondition.from(h.weatherCode, h.isDay).iconRes(), m.iconDp, palette)
                Spacer(GlanceModifier.height(2.dp))
                LabelText(context, formatTemp(h.tempC, s.fahrenheit), m.pillSp - 0.5f, palette)
            }
        }
    }
}

/** Up to two smart items that fit on one line across the widget (measured, joined with " • "). */
private fun pickSmart(items: List<SmartLine.Item>, widthDp: Float, sp: Float, widths: TextWidths): List<SmartLine.Item> {
    val out = mutableListOf<SmartLine.Item>()
    for (item in items) {
        if (out.size >= 2) break
        val candidate = (out + item).joinToString(" • ") { it.text }
        if (out.isNotEmpty() && widths.dp(candidate, sp) > widthDp) break
        out += item
    }
    return out
}

/** Smart line wording from string resources. */
private fun smartLabels(context: Context, fahrenheit: Boolean) = SmartLine.Labels(
    now = context.getString(R.string.event_now),
    today = context.getString(R.string.event_today),
    tomorrow = context.getString(R.string.event_tomorrow),
    inMinutes = { context.getString(R.string.smart_in_minutes, it) },
    rainFrom = { context.getString(R.string.smart_rain_from, it) },
    rainEasing = { context.getString(R.string.smart_rain_easing, it) },
    rainContinuing = context.getString(R.string.smart_rain_continuing),
    alarm = { context.getString(R.string.smart_alarm, it) },
    warning = { level, hazard ->
        val name = context.getString(
            when (level) {
                WarningLevel.Yellow -> R.string.warning_yellow
                WarningLevel.Amber -> R.string.warning_amber
                WarningLevel.Red -> R.string.warning_red
            }
        )
        context.getString(R.string.smart_warning, name, hazard)
    },
    chargeFull = { context.getString(R.string.smart_charge_full, it) },
    chargeTo80 = { context.getString(R.string.smart_charge_80, it) },
    duration = { m ->
        if (m < 60) context.getString(R.string.duration_min, m)
        else context.getString(R.string.duration_h_min, m / 60, m % 60)
    },
    uvHighUntil = { uv, t -> context.getString(R.string.smart_uv_until, uv, t) },
    uvHighFrom = { uv, t -> context.getString(R.string.smart_uv_from, uv, t) },
    airPoor = { context.getString(R.string.smart_air_poor, it) },
    pollenHigh = { type, level ->
        context.getString(R.string.smart_pollen_high, context.getString(level.shortLabelRes()), context.getString(type.labelRes()).lowercase())
    },
    goldenEvening = { context.getString(R.string.smart_golden_evening, it) },
    goldenMorning = { context.getString(R.string.smart_golden_morning, it) },
    frost = { context.getString(R.string.smart_frost, it) },
    temp = { formatTemp(it, fahrenheit) },
)

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
