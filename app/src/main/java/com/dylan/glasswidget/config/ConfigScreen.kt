package com.dylan.glasswidget.config

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.dylan.glasswidget.R
import com.dylan.glasswidget.data.DatePreset
import com.dylan.glasswidget.data.DeviceLocation
import com.dylan.glasswidget.data.GeocodingResult
import com.dylan.glasswidget.data.GlassVariant
import com.dylan.glasswidget.data.HourMode
import com.dylan.glasswidget.data.Limits
import com.dylan.glasswidget.data.LocationMode
import com.dylan.glasswidget.data.OpenMeteoApi
import com.dylan.glasswidget.data.PaddingMode
import com.dylan.glasswidget.data.TempUnit
import com.dylan.glasswidget.data.TintMode
import com.dylan.glasswidget.data.WeatherRefreshWorker
import com.dylan.glasswidget.widget.AppTargets
import com.dylan.glasswidget.widget.GlassClockWidget
import com.dylan.glasswidget.widget.WidgetPrefsKeys
import com.dylan.glasswidget.widget.WidgetSettings
import com.dylan.glasswidget.widget.Zone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Settings for one placed widget. Every change is written to the widget's own state and pushed to
 * the home screen immediately — the widget itself is the live preview.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConfigScreen(appWidgetId: Int, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val glanceId = remember(appWidgetId) { GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId) }

    var loaded by remember { mutableStateOf<WidgetSettings?>(null) }
    LaunchedEffect(glanceId) {
        loaded = WidgetSettings.from(getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId))
    }

    fun edit(block: (MutablePreferences) -> Unit) {
        scope.launch {
            updateAppWidgetState(context, glanceId) { block(it) }
            loaded = WidgetSettings.from(getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId))
            GlassClockWidget().update(context, glanceId)
        }
    }

    val s = loaded
    Scaffold(
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = {
                        WeatherRefreshWorker.schedule(context)
                        WeatherRefreshWorker.refreshNow(context)
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) { Text(stringResource(R.string.config_done)) }
            }
        },
    ) { inner ->
        if (s == null) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(stringResource(R.string.config_title), style = MaterialTheme.typography.headlineMedium)

            // ---- Weather -------------------------------------------------------------------------
            Section(stringResource(R.string.section_weather)) {
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
                    listOf(
                        LocationMode.City to stringResource(R.string.loc_city),
                        LocationMode.Device to stringResource(R.string.loc_device),
                    ),
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
                    CityPicker(
                        currentName = s.cityName,
                        hasCity = s.cityLat != null,
                        onPick = { place ->
                            edit {
                                it[WidgetPrefsKeys.LOCATION_MODE] = LocationMode.City.key
                                it[WidgetPrefsKeys.CITY_LAT] = place.latitude
                                it[WidgetPrefsKeys.CITY_LON] = place.longitude
                                it[WidgetPrefsKeys.CITY_NAME] = place.displayName
                            }
                            WeatherRefreshWorker.refreshNow(context)
                        },
                    )
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

                TextButton(onClick = { WeatherRefreshWorker.refreshNow(context) }) {
                    Text(stringResource(R.string.refresh_now))
                }
            }

            // ---- Glass ---------------------------------------------------------------------------
            Section(stringResource(R.string.section_glass)) {
                Label(stringResource(R.string.glass_variant))
                Chips(
                    listOf(
                        GlassVariant.Soft to stringResource(R.string.variant_soft),
                        GlassVariant.Clear to stringResource(R.string.variant_clear),
                    ),
                    selected = s.variant,
                ) { v -> edit { it[WidgetPrefsKeys.GLASS_VARIANT] = v.key } }

                var radiusIndex by remember(s.cornerRadiusDp) {
                    mutableFloatStateOf(Limits.RADII.indexOf(s.cornerRadiusDp).coerceAtLeast(0).toFloat())
                }
                val radius = Limits.RADII[radiusIndex.roundToInt().coerceIn(0, Limits.RADII.lastIndex)]
                Label(stringResource(R.string.corner_radius, radius))
                Slider(
                    value = radiusIndex,
                    onValueChange = { radiusIndex = it },
                    valueRange = 0f..Limits.RADII.lastIndex.toFloat(),
                    steps = Limits.RADII.size - 2,
                    onValueChangeFinished = { edit { it[WidgetPrefsKeys.CORNER_RADIUS] = radius } },
                )

                Label(stringResource(R.string.tint))
                Chips(
                    listOf(
                        TintMode.Dynamic to stringResource(R.string.tint_dynamic),
                        TintMode.Light to stringResource(R.string.tint_light),
                        TintMode.Dark to stringResource(R.string.tint_dark),
                    ),
                    selected = s.tint,
                ) { t -> edit { it[WidgetPrefsKeys.TINT_MODE] = t.key } }
            }

            // ---- Size ----------------------------------------------------------------------------
            Section(stringResource(R.string.section_size)) {
                var scale by remember(s.textScale) { mutableFloatStateOf(s.textScale) }
                Label(stringResource(R.string.text_scale, (scale * 100).roundToInt()))
                Slider(
                    value = scale,
                    onValueChange = { scale = it },
                    valueRange = Limits.TEXT_SCALE_MIN..Limits.TEXT_SCALE_MAX,
                    steps = 4,
                    onValueChangeFinished = { edit { it[WidgetPrefsKeys.TEXT_SCALE] = Limits.clampTextScale(scale) } },
                )

                Label(stringResource(R.string.padding))
                Chips(
                    listOf(
                        PaddingMode.Tight to stringResource(R.string.padding_tight),
                        PaddingMode.Normal to stringResource(R.string.padding_normal),
                        PaddingMode.Roomy to stringResource(R.string.padding_roomy),
                    ),
                    selected = s.padding,
                ) { p -> edit { it[WidgetPrefsKeys.PADDING_MODE] = p.key } }
            }

            // ---- Clock & date --------------------------------------------------------------------
            Section(stringResource(R.string.section_clock_date)) {
                Label(stringResource(R.string.hour_mode))
                Chips(
                    listOf(
                        HourMode.System to stringResource(R.string.hour_system),
                        HourMode.H12 to stringResource(R.string.hour_12),
                        HourMode.H24 to stringResource(R.string.hour_24),
                    ),
                    selected = s.hourMode,
                ) { h -> edit { it[WidgetPrefsKeys.HOUR_MODE] = h.key } }

                Label(stringResource(R.string.date_format))
                val now = remember { Date() }
                Chips(
                    DatePreset.entries.map { it to SimpleDateFormat(it.pattern, Locale.getDefault()).format(now) },
                    selected = s.datePreset,
                ) { d -> edit { it[WidgetPrefsKeys.DATE_FORMAT_PRESET] = d.key } }
            }

            // ---- Tap targets ---------------------------------------------------------------------
            Section(stringResource(R.string.section_taps)) {
                TapTargetRow(stringResource(R.string.tap_clock), Zone.Clock, s.clockApp) { pick ->
                    edit { setOrRemove(it, WidgetPrefsKeys.CLOCK_APP_PACKAGE, pick) }
                }
                TapTargetRow(stringResource(R.string.tap_date), Zone.Date, s.dateApp) { pick ->
                    edit { setOrRemove(it, WidgetPrefsKeys.DATE_APP_PACKAGE, pick) }
                }
                TapTargetRow(stringResource(R.string.tap_weather), Zone.Weather, s.weatherApp) { pick ->
                    edit { setOrRemove(it, WidgetPrefsKeys.WEATHER_APP_PACKAGE, pick) }
                }
            }

            // ---- Reliability ---------------------------------------------------------------------
            Section(stringResource(R.string.section_reliability)) {
                ReliabilityCard()
            }
        }
    }
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

@OptIn(ExperimentalLayoutApi::class)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}
