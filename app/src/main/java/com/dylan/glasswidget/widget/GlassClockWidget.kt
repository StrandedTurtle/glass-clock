package com.dylan.glasswidget.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.dylan.glasswidget.R
import com.dylan.glasswidget.data.WeatherCache
import com.dylan.glasswidget.data.WeatherSnapshot
import com.dylan.glasswidget.data.WeatherStore
import com.dylan.glasswidget.data.WidgetSize
import com.dylan.glasswidget.data.formatTemp
import com.dylan.glasswidget.data.TempUnit
import kotlin.math.roundToInt

class GlassClockWidget : GlanceAppWidget() {

    // Three breakpoints; the launcher picks one, so text sizes step rather than glide.
    // The smallest equals the provider's minResize size, so there is never a size without a layout.
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(180.dp, 70.dp), DpSize(250.dp, 110.dp), DpSize(250.dp, 180.dp))
    )

    // Per-placed-widget settings live in Glance's own per-instance Preferences state.
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val initialWeather = WeatherCache.load(context)
        provideContent {
            // Collected, not read once, so a refresh landing while a session is alive still shows up.
            val weather by WeatherCache.flow(context).collectAsState(initialWeather)
            val settings = WidgetSettings.from(currentState<Preferences>())
            GlassClockContent(context, appWidgetId, settings, weather)
        }
    }
}

/** Sizes that depend on the breakpoint. Text sizes are multiplied by the user's text scale. */
private data class Metrics(
    val clockSp: Float,
    val dateSp: Float,
    val tempSp: Float,
    val detailSp: Float,
    val iconDp: Int,
    val padDp: Int,
) {
    companion object {
        fun of(size: WidgetSize, scale: Float, paddingFactor: Float): Metrics {
            val base = when (size) {
                WidgetSize.Compact -> Metrics(30f, 12f, 12f, 11f, 14, 8)
                WidgetSize.Standard -> Metrics(44f, 14f, 16f, 12f, 20, 14)
                WidgetSize.Tall -> Metrics(52f, 15f, 18f, 13f, 22, 18)
            }
            // The compact widget is only ~70dp tall; don't let a large scale push text out of it.
            val s = if (size == WidgetSize.Compact) scale.coerceAtMost(1.1f) else scale
            return Metrics(
                clockSp = base.clockSp * s,
                dateSp = base.dateSp * s,
                tempSp = base.tempSp * s,
                detailSp = base.detailSp * s,
                iconDp = (base.iconDp * s).roundToInt(),
                padDp = (base.padDp * paddingFactor).roundToInt(),
            )
        }
    }
}

@Composable
private fun GlassClockContent(
    context: Context,
    appWidgetId: Int,
    settings: WidgetSettings,
    store: WeatherStore,
) {
    val size = WidgetSize.fromHeightDp(LocalSize.current.height.value)
    val m = Metrics.of(size, settings.textScale, settings.padding.factor)
    val palette = GlassPalette.of(context, settings.tint, settings.variant)

    val location = settings.location
    val weather: WeatherSnapshot? = location?.let { store.entries[it.key] }

    // Resolved at render time; null means the zone is inert.
    val clockIntent = AppTargets.launchIntent(context, Zone.Clock, settings.clockApp)
    val dateIntent = AppTargets.launchIntent(context, Zone.Date, settings.dateApp)
    // Until a city is chosen, tapping the weather zone opens settings so it is never a dead end.
    val weatherIntent: Intent? =
        if (location == null) AppTargets.configIntent(context, appWidgetId)
        else AppTargets.launchIntent(context, Zone.Weather, settings.weatherApp)

    val placeholder = context.getString(
        if (location == null) R.string.set_location else R.string.temp_placeholder
    )
    val weatherRow: @Composable () -> Unit = {
        WeatherRow(context, weather, settings.tempUnit, m.iconDp, m.tempSp, palette, placeholder)
    }
    val pad = m.padDp.dp

    GlassBackground(palette, settings.variant, settings.cornerRadiusDp) {
        when (size) {
            // Clock, plus one line: date and temperature.
            WidgetSize.Compact -> Column(
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = pad + 4.dp, vertical = pad),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TapZone(clockIntent) { ClockText(context, settings.hourMode, m.clockSp, palette) }
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TapZone(dateIntent) { DateText(context, settings.datePreset, m.dateSp, palette) }
                    Spacer(GlanceModifier.defaultWeight())
                    TapZone(weatherIntent, contentAlignment = Alignment.CenterEnd, content = weatherRow)
                }
            }

            // Big clock; date and weather share the row below, each its own tap target that
            // grows to fill the remaining height.
            WidgetSize.Standard -> Column(
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = pad + 2.dp, vertical = pad),
            ) {
                TapZone(clockIntent, GlanceModifier.fillMaxWidth()) {
                    ClockText(context, settings.hourMode, m.clockSp, palette)
                }
                Row(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TapZone(dateIntent, GlanceModifier.fillMaxHeight()) {
                        DateText(context, settings.datePreset, m.dateSp, palette)
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    TapZone(weatherIntent, GlanceModifier.fillMaxHeight(), Alignment.CenterEnd, weatherRow)
                }
            }

            // Standard, plus condition text, today's high/low and the city.
            WidgetSize.Tall -> Column(
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = pad + 2.dp, vertical = pad),
            ) {
                TapZone(clockIntent, GlanceModifier.fillMaxWidth()) {
                    ClockText(context, settings.hourMode, m.clockSp, palette)
                }
                Row(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TapZone(dateIntent, GlanceModifier.fillMaxHeight()) {
                        Column {
                            DateText(context, settings.datePreset, m.dateSp, palette)
                            location?.label?.takeIf { it.isNotBlank() }?.let {
                                LabelText(context, it.substringBefore(','), m.detailSp, palette, secondary = true)
                            }
                        }
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    TapZone(weatherIntent, GlanceModifier.fillMaxHeight(), Alignment.CenterEnd) {
                        Column(horizontalAlignment = Alignment.End) {
                            weatherRow()
                            if (weather != null) {
                                LabelText(
                                    context, context.getString(weather.condition().labelRes()),
                                    m.detailSp, palette, secondary = true,
                                )
                                LabelText(context, highLow(context, weather, settings.tempUnit), m.detailSp, palette, secondary = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun highLow(context: Context, w: WeatherSnapshot, unit: TempUnit): String {
    val f = unit == TempUnit.F
    return context.getString(R.string.high_low, formatTemp(w.tempMaxC, f), formatTemp(w.tempMinC, f))
}
