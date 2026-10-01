package com.dylan.glasswidget.config

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.dylan.glasswidget.R
import com.dylan.glasswidget.data.CalendarLink
import com.dylan.glasswidget.data.CalendarLinks
import com.dylan.glasswidget.data.Ics
import com.dylan.glasswidget.data.CalendarRefreshWorker
import com.dylan.glasswidget.data.CalendarRepository
import com.dylan.glasswidget.data.ClockFace
import com.dylan.glasswidget.data.ClockStyle
import com.dylan.glasswidget.data.DatePreset
import com.dylan.glasswidget.data.DeviceLocation
import com.dylan.glasswidget.data.GeocodingResult
import com.dylan.glasswidget.data.GlassVariant
import com.dylan.glasswidget.data.HourMode
import com.dylan.glasswidget.data.Limits
import com.dylan.glasswidget.data.LocationMode
import com.dylan.glasswidget.data.OpenMeteoApi
import com.dylan.glasswidget.data.TempUnit
import com.dylan.glasswidget.data.TintMode
import com.dylan.glasswidget.data.WeatherDetail
import com.dylan.glasswidget.data.WeatherRefreshWorker
import com.dylan.glasswidget.data.WidgetAlignment
import com.dylan.glasswidget.data.WidgetDataStore
import com.dylan.glasswidget.data.WidgetText
import com.dylan.glasswidget.widget.AppTargets
import com.dylan.glasswidget.widget.ClockTicker
import com.dylan.glasswidget.widget.GlassClockWidget
import com.dylan.glasswidget.widget.WidgetPrefsKeys
import com.dylan.glasswidget.widget.WidgetSettings
import com.dylan.glasswidget.widget.Zone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PreviewSize(val heightDp: Int?) { This(null), Compact(70), Standard(110), Tall(180), Large(250) }

/**
 * Settings for one placed widget. The top of the screen is transparent, so the real home-screen
 * wallpaper shows through behind a live render of the widget; every change is written straight to
 * the widget's state, pushed to the home screen and re-rendered in the preview.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConfigScreen(appWidgetId: Int, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val glanceId = remember(appWidgetId) { GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId) }

    var loaded by remember { mutableStateOf<WidgetSettings?>(null) }
    var previewVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(glanceId) {
        loaded = WidgetSettings.from(getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId))
    }
    // New weather or calendar data also refreshes the preview.
    val data by WidgetDataStore.flow(context).collectAsState(initial = null)
    LaunchedEffect(data) { previewVersion++ }

    fun edit(block: (MutablePreferences) -> Unit) {
        scope.launch {
            updateAppWidgetState(context, glanceId) { block(it) }
            loaded = WidgetSettings.from(getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId))
            GlassClockWidget().update(context, glanceId)
            ClockTicker.tick(context) // starts or stops the minute ticker if the clock style changed
            previewVersion++
        }
    }

    var previewSize by remember { mutableStateOf(PreviewSize.This) }
    val placed = remember(appWidgetId) { placedWidgetSize(context, appWidgetId) }

    Column(Modifier.fillMaxSize()) {
        // ---- Live preview on the real wallpaper ------------------------------------------------
        BoxWithConstraints(Modifier.fillMaxWidth().statusBarsPadding()) {
            val width = placed?.width?.coerceAtMost(maxWidth) ?: (maxWidth - 32.dp)
            val size = when (val h = previewSize.heightDp) {
                null -> placed?.let { DpSize(width, it.height) } ?: DpSize(width, 180.dp)
                else -> DpSize(width, h.dp)
            }
            Box(
                Modifier.fillMaxWidth().height(maxOf(size.height + 40.dp, 200.dp)),
                contentAlignment = Alignment.Center,
            ) {
                WidgetPreview(glanceId, size, previewVersion)
            }
        }

        // ---- Settings sheet --------------------------------------------------------------------
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 2.dp,
        ) {
            val s = loaded
            Column(Modifier.fillMaxSize()) {
                if (s != null) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Label(stringResource(R.string.preview_size))
                            Chips(
                                listOf(
                                    PreviewSize.This to stringResource(R.string.preview_this),
                                    PreviewSize.Compact to stringResource(R.string.preview_compact),
                                    PreviewSize.Standard to stringResource(R.string.preview_standard),
                                    PreviewSize.Tall to stringResource(R.string.preview_tall),
                                    PreviewSize.Large to stringResource(R.string.preview_large),
                                ),
                                selected = previewSize,
                            ) { previewSize = it }
                        }
                        LookSection(s, ::edit)
                        ClockDateSection(s, ::edit)
                        WeatherSection(s, ::edit)
                        CalendarSection(s, ::edit) { previewVersion++ }
                        TapTargetSection(s, ::edit)
                        Section(stringResource(R.string.section_reliability)) { ReliabilityCard() }
                    }
                } else {
                    Box(Modifier.weight(1f)) {}
                }
                Button(
                    onClick = {
                        WeatherRefreshWorker.schedule(context)
                        WeatherRefreshWorker.refreshNow(context)
                        CalendarRefreshWorker.observe(context)
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                ) { Text(stringResource(R.string.config_done)) }
            }
        }
    }
}

private typealias Edit = ((MutablePreferences) -> Unit) -> Unit

@Composable
private fun LookSection(s: WidgetSettings, edit: Edit) = Section(stringResource(R.string.section_look)) {
    Label(stringResource(R.string.clock_style))
    Chips(
        listOf(
            ClockStyle.Glass to stringResource(R.string.style_glass),
            ClockStyle.Solid to stringResource(R.string.style_solid),
        ),
        selected = s.clockStyle,
    ) { v -> edit { it[WidgetPrefsKeys.CLOCK_STYLE] = v.key } }

    if (s.clockStyle == ClockStyle.Glass) {
        Label(stringResource(R.string.clock_face))
        Chips(ClockFace.entries.map { it to it.label }, selected = s.clockFace) { f ->
            edit { it[WidgetPrefsKeys.CLOCK_FACE] = f.key }
        }
    }
    Text(
        stringResource(R.string.style_glass_note),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Label(stringResource(R.string.glass_variant))
    Chips(
        listOf(GlassVariant.Soft to stringResource(R.string.variant_soft), GlassVariant.Clear to stringResource(R.string.variant_clear)),
        selected = s.variant,
    ) { v -> edit { it[WidgetPrefsKeys.GLASS_VARIANT] = v.key } }

    Label(stringResource(R.string.tint))
    Chips(
        listOf(
            TintMode.Frost to stringResource(R.string.tint_frost),
            TintMode.Smoke to stringResource(R.string.tint_smoke),
            TintMode.Ink to stringResource(R.string.tint_ink),
            TintMode.Dynamic to stringResource(R.string.tint_dynamic),
        ),
        selected = s.tint,
    ) { t -> edit { it[WidgetPrefsKeys.TINT_MODE] = t.key } }

    Label(stringResource(R.string.alignment))
    Chips(
        listOf(WidgetAlignment.Center to stringResource(R.string.align_center), WidgetAlignment.Start to stringResource(R.string.align_start)),
        selected = s.alignment,
    ) { a -> edit { it[WidgetPrefsKeys.ALIGNMENT] = a.key } }

    var clock by remember(s.clockScale) { mutableFloatStateOf(s.clockScale) }
    Label(stringResource(R.string.clock_scale, (clock * 100).roundToInt()))
    Slider(
        value = clock,
        onValueChange = { clock = it },
        valueRange = Limits.CLOCK_SCALE_MIN..Limits.CLOCK_SCALE_MAX,
        steps = 9,
        onValueChangeFinished = { edit { it[WidgetPrefsKeys.CLOCK_SCALE] = Limits.clampClockScale(clock) } },
    )

    var text by remember(s.textScale) { mutableFloatStateOf(s.textScale) }
    Label(stringResource(R.string.text_scale, (text * 100).roundToInt()))
    Slider(
        value = text,
        onValueChange = { text = it },
        valueRange = Limits.TEXT_SCALE_MIN..Limits.TEXT_SCALE_MAX,
        steps = 4,
        onValueChangeFinished = { edit { it[WidgetPrefsKeys.TEXT_SCALE] = Limits.clampTextScale(text) } },
    )
}

@Composable
private fun ClockDateSection(s: WidgetSettings, edit: Edit) = Section(stringResource(R.string.section_clock_date)) {
    Label(stringResource(R.string.hour_mode))
    Chips(
        listOf(
            HourMode.System to stringResource(R.string.hour_system),
            HourMode.H12 to stringResource(R.string.hour_12),
            HourMode.H24 to stringResource(R.string.hour_24),
        ),
        selected = s.hourMode,
    ) { h -> edit { it[WidgetPrefsKeys.HOUR_MODE] = h.key } }

    SwitchRow(stringResource(R.string.show_colon), null, s.showColon) { on -> edit { it[WidgetPrefsKeys.SHOW_COLON] = on } }

    Label(stringResource(R.string.date_format))
    val now = remember { Date() }
    Chips(
        DatePreset.entries.map { it to SimpleDateFormat(it.pattern, Locale.getDefault()).format(now) },
        selected = s.datePreset,
    ) { d -> edit { it[WidgetPrefsKeys.DATE_FORMAT_PRESET] = d.key } }
}

@Composable
private fun WeatherSection(s: WidgetSettings, edit: Edit) = Section(stringResource(R.string.section_weather)) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }

    fun useDeviceLocation() {
        scope.launch {
            val fix = DeviceLocation.current(context)
            if (fix == null) {
                message = context.getString(R.string.device_unavailable)
            } else {
                message = null
                edit {
                    it[WidgetPrefsKeys.LOCATION_MODE] = LocationMode.Device.key
                    it[WidgetPrefsKeys.DEVICE_LAT] = fix.lat
                    it[WidgetPrefsKeys.DEVICE_LON] = fix.lon
                }
                WeatherRefreshWorker.refreshNow(context)
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) useDeviceLocation() else message = context.getString(R.string.device_denied)
    }

    Label(stringResource(R.string.loc_mode))
    Chips(
        listOf(LocationMode.City to stringResource(R.string.loc_city), LocationMode.Device to stringResource(R.string.loc_device)),
        selected = s.locationMode,
    ) { mode ->
        if (mode == LocationMode.City) {
            edit { it[WidgetPrefsKeys.LOCATION_MODE] = LocationMode.City.key }
            WeatherRefreshWorker.refreshNow(context)
        } else if (DeviceLocation.hasPermission(context)) {
            useDeviceLocation()
        } else {
            permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    if (s.locationMode == LocationMode.City) {
        CityPicker(currentName = s.cityName, hasCity = s.cityLat != null) { place ->
            edit {
                it[WidgetPrefsKeys.LOCATION_MODE] = LocationMode.City.key
                it[WidgetPrefsKeys.CITY_LAT] = place.latitude
                it[WidgetPrefsKeys.CITY_LON] = place.longitude
                it[WidgetPrefsKeys.CITY_NAME] = place.displayName
            }
            WeatherRefreshWorker.refreshNow(context)
        }
    } else {
        Text(
            if (s.deviceLat != null && s.deviceLon != null)
                stringResource(
                    R.string.device_current,
                    String.format(Locale.US, "%.2f", s.deviceLat),
                    String.format(Locale.US, "%.2f", s.deviceLon),
                )
            else stringResource(R.string.device_none),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = {
            if (DeviceLocation.hasPermission(context)) useDeviceLocation()
            else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }) { Text(stringResource(R.string.device_use)) }
    }
    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    Label(stringResource(R.string.temp_unit))
    Chips(
        listOf(TempUnit.C to stringResource(R.string.unit_c), TempUnit.F to stringResource(R.string.unit_f)),
        selected = s.tempUnit,
    ) { u -> edit { it[WidgetPrefsKeys.TEMP_UNIT] = u.key } }

    Label(stringResource(R.string.weather_details))
    val labels = mapOf(
        WeatherDetail.Condition to R.string.detail_condition,
        WeatherDetail.HighLow to R.string.detail_high_low,
        WeatherDetail.SunTimes to R.string.detail_sun,
        WeatherDetail.FeelsLike to R.string.detail_feels,
        WeatherDetail.RainChance to R.string.detail_rain,
        WeatherDetail.Wind to R.string.detail_wind,
        WeatherDetail.Humidity to R.string.detail_humidity,
        WeatherDetail.Uv to R.string.detail_uv,
        WeatherDetail.AirQuality to R.string.detail_aqi,
    )
    MultiChips(WeatherDetail.entries.map { it to stringResource(labels.getValue(it)) }, s.details) { d, on ->
        val next = if (on) s.details + d else s.details - d
        edit { it[WidgetPrefsKeys.WEATHER_DETAILS] = next.map(WeatherDetail::key).toSet() }
    }

    TextButton(onClick = { WeatherRefreshWorker.refreshNow(context) }) { Text(stringResource(R.string.refresh_now)) }
}

@Composable
private fun CalendarSection(s: WidgetSettings, edit: Edit, onEventsLoaded: () -> Unit) =
    Section(stringResource(R.string.section_calendar)) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var permissionMessage by remember { mutableStateOf<String?>(null) }
        var visible by remember { mutableStateOf<List<CalendarRepository.PhoneCalendar>?>(null) }
        var hasPermission by remember { mutableStateOf(CalendarRepository.hasPermission(context)) }
        val data by WidgetDataStore.flow(context).collectAsState(initial = null)

        fun reload() {
            scope.launch {
                visible = withContext(Dispatchers.IO) { CalendarRepository.visibleCalendars(context) }
                withContext(Dispatchers.IO) { CalendarRefreshWorker.refreshAndRedraw(context, fetchLinks = true) }
                onEventsLoaded()
            }
        }
        LaunchedEffect(hasPermission) {
            visible = withContext(Dispatchers.IO) { CalendarRepository.visibleCalendars(context) }
        }

        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            hasPermission = ok
            if (ok) {
                permissionMessage = null
                edit { it[WidgetPrefsKeys.USE_DEVICE_CALENDARS] = true }
                CalendarRefreshWorker.observe(context)
                reload()
            } else {
                permissionMessage = context.getString(R.string.calendar_denied)
            }
        }

        SwitchRow(stringResource(R.string.show_events), stringResource(R.string.show_events_body), s.showEvents) { on ->
            edit { it[WidgetPrefsKeys.SHOW_EVENTS] = on }
            if (on) reload()
        }
        if (!s.showEvents) return@Section
        Expandable(stringResource(R.string.cal_how_title)) { Hint(stringResource(R.string.cal_how_body)) }

        // ---- Source 1: calendars synced into Android ----
        SwitchRow(stringResource(R.string.cal_phone_switch), stringResource(R.string.cal_phone_sub), s.useDeviceCalendars && hasPermission) { on ->
            if (!on) edit { it[WidgetPrefsKeys.USE_DEVICE_CALENDARS] = false }
            else if (hasPermission) { edit { it[WidgetPrefsKeys.USE_DEVICE_CALENDARS] = true }; reload() }
            else permission.launch(Manifest.permission.READ_CALENDAR)
        }
        if (s.useDeviceCalendars && hasPermission) {
            val calendars = visible
            val upcoming = data?.events?.count { !it.fromLink } ?: 0
            when {
                calendars == null -> Hint(stringResource(R.string.cal_phone_checking))
                calendars.isEmpty() -> Hint(stringResource(R.string.cal_phone_none))
                else -> Expandable(stringResource(R.string.cal_phone_summary, calendars.size, upcoming)) {
                    calendars.groupBy { it.account }.forEach { (account, list) ->
                        Text(account, style = MaterialTheme.typography.labelLarge)
                        list.forEach { Hint("• " + it.name) }
                    }
                    Hint(stringResource(R.string.cal_phone_missing))
                }
            }
        }
        permissionMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        // ---- Source 2: calendar links (.ics), e.g. Proton ----
        Label(stringResource(R.string.cal_link_title))
        val linkCounts = data?.events.orEmpty().filter { it.fromLink }.groupingBy { it.source }.eachCount()
        s.calendarLinks.forEach { link ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(link.name, style = MaterialTheme.typography.bodyLarge)
                    Hint(stringResource(R.string.cal_link_item, Ics.shortLabel(link.url), linkCounts[link.source] ?: 0))
                }
                TextButton(onClick = {
                    edit { it[WidgetPrefsKeys.CALENDAR_LINKS] = CalendarLink.encode(s.calendarLinks - link) }
                    reload()
                }) { Text(stringResource(R.string.cal_link_remove)) }
            }
        }
        if (s.calendarLinks.isEmpty()) {
            Hint(stringResource(R.string.cal_link_empty))
        } else {
            // Links can't tell us when they change, so show when they were last read and allow a manual refresh.
            val fetched = data?.linksFetchedAtMs ?: 0L
            Row(verticalAlignment = Alignment.CenterVertically) {
                Hint(
                    if (fetched == 0L) stringResource(R.string.cal_link_never)
                    else stringResource(
                        R.string.cal_link_updated,
                        WidgetText.clock(fetched, java.time.ZoneId.systemDefault(), android.text.format.DateFormat.is24HourFormat(context)),
                    ),
                )
                TextButton(onClick = { reload() }) { Text(stringResource(R.string.cal_link_refresh)) }
            }
        }
        var adding by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { adding = true }) { Text(stringResource(R.string.cal_link_add)) }
        if (adding) {
            AddLinkDialog(
                existing = s.calendarLinks,
                onAdded = { link ->
                    adding = false
                    edit { it[WidgetPrefsKeys.CALENDAR_LINKS] = CalendarLink.encode(s.calendarLinks + link) }
                    reload()
                },
                onDismiss = { adding = false },
            )
        }

        SwitchRow(stringResource(R.string.events_all_day), null, s.eventsAllDay) { on ->
            edit { it[WidgetPrefsKeys.EVENTS_ALL_DAY] = on }
        }
    }

/** Paste a link; it's downloaded and checked before it's saved, under the calendar's own name. */
@Composable
private fun AddLinkDialog(existing: List<CalendarLink>, onAdded: (CalendarLink) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!checking) onDismiss() },
        title = { Text(stringResource(R.string.cal_link_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Hint(stringResource(R.string.cal_link_steps))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = null },
                    singleLine = true,
                    label = { Text(stringResource(R.string.cal_link_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (checking) Hint(stringResource(R.string.cal_link_checking))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank() && !checking,
                onClick = {
                    val clean = url.trim()
                    if (existing.any { it.source == Ics.sourceKey(clean) }) {
                        error = context.getString(R.string.cal_link_duplicate); return@TextButton
                    }
                    checking = true
                    scope.launch {
                        runCatching { CalendarLinks.load(clean) }
                            .onSuccess { onAdded(CalendarLink(it.name ?: Ics.shortLabel(clean), clean)) }
                            .onFailure { error = context.getString(R.string.cal_link_failed, it.message ?: it.javaClass.simpleName) }
                        checking = false
                    }
                },
            ) { Text(stringResource(R.string.cal_link_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !checking) { Text(stringResource(R.string.picker_close)) } },
    )
}

/** A heading that shows or hides its details. */
@Composable
private fun Expandable(title: String, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(if (open) "▲" else "▼", color = MaterialTheme.colorScheme.primary)
        }
        if (open) Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun TapTargetSection(s: WidgetSettings, edit: Edit) = Section(stringResource(R.string.section_taps)) {
    TapTargetRow(stringResource(R.string.tap_clock), Zone.Clock, s.clockApp) { pick ->
        edit { setOrRemove(it, WidgetPrefsKeys.CLOCK_APP_PACKAGE, pick) }
    }
    TapTargetRow(stringResource(R.string.tap_date), Zone.Date, s.dateApp) { pick ->
        edit { setOrRemove(it, WidgetPrefsKeys.DATE_APP_PACKAGE, pick) }
    }
    TapTargetRow(stringResource(R.string.tap_weather), Zone.Weather, s.weatherApp) { pick ->
        edit { setOrRemove(it, WidgetPrefsKeys.WEATHER_APP_PACKAGE, pick) }
    }
    Text(
        stringResource(R.string.tap_event_note),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** null removes the key (= "use the default app"); otherwise stores the package, or "" for nothing. */
private fun setOrRemove(p: MutablePreferences, key: Preferences.Key<String>, v: String?) {
    if (v == null) p.remove(key) else p[key] = v
}

@Composable
private fun TapTargetRow(title: String, zone: Zone, chosen: String?, onPick: (String?) -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val description = when {
        chosen == null -> {
            val def = AppTargets.defaultPackage(context.packageManager, zone)
            if (def != null) "${stringResource(R.string.tap_default)} · ${InstalledApps.labelFor(context, def)}"
            else stringResource(R.string.tap_default)
        }
        chosen.isEmpty() -> stringResource(R.string.tap_none)
        else -> InstalledApps.labelFor(context, chosen)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (open) AppPickerDialog(onPick = { open = false; onPick(it) }, onDismiss = { open = false })
}

@Composable
private fun CityPicker(currentName: String?, hasCity: Boolean, onPick: (GeocodingResult) -> Unit) {
    val api = remember { OpenMeteoApi() }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeocodingResult>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    val noResults = stringResource(R.string.city_no_results)
    val error = stringResource(R.string.city_error)

    // Debounced search as the user types.
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); status = null; return@LaunchedEffect }
        delay(400)
        runCatching { api.searchCities(query) }
            .onSuccess { results = it; status = if (it.isEmpty()) noResults else null }
            .onFailure { results = emptyList(); status = error }
    }

    Text(
        if (hasCity) stringResource(R.string.city_current, currentName ?: "") else stringResource(R.string.city_none),
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        singleLine = true,
        label = { Text(stringResource(R.string.city_search_hint)) },
        modifier = Modifier.fillMaxWidth(),
    )
    status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Column {
        results.forEach { place ->
            Text(
                place.displayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(place); query = ""; results = emptyList() }
                    .padding(vertical = 12.dp),
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun ReliabilityCard() {
    val context = LocalContext.current
    Text(stringResource(R.string.reliability_body), style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { Reliability.openBatterySettings(context) }) { Text(stringResource(R.string.open_battery)) }
        OutlinedButton(onClick = { Reliability.openAutostart(context) }) { Text(stringResource(R.string.open_autostart)) }
    }
    TextButton(onClick = { Reliability.openAppInfo(context) }) { Text(stringResource(R.string.open_app_info)) }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelLarge)

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun <T> MultiChips(options: List<Pair<T, String>>, selected: Set<T>, onToggle: (T, Boolean) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = value in selected
            FilterChip(selected = on, onClick = { onToggle(value, !on) }, label = { Text(label) })
        }
    }
}
