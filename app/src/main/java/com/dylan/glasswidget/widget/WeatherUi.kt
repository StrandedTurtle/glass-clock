package com.dylan.glasswidget.widget

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.size
import com.dylan.glasswidget.R
import com.dylan.glasswidget.data.Moon
import com.dylan.glasswidget.data.MoonPhase
import com.dylan.glasswidget.data.Pollen
import com.dylan.glasswidget.data.PollenLevel
import com.dylan.glasswidget.data.PollenType
import com.dylan.glasswidget.data.WeatherCondition
import com.dylan.glasswidget.data.WeatherDetail
import com.dylan.glasswidget.data.WeatherSnapshot
import com.dylan.glasswidget.data.WidgetText
import com.dylan.glasswidget.data.formatTemp
import com.dylan.glasswidget.data.formatUv
import com.dylan.glasswidget.data.formatWind

@DrawableRes
fun WeatherCondition.iconRes(): Int = when (this) {
    WeatherCondition.ClearDay -> R.drawable.ic_wx_clear_day
    WeatherCondition.ClearNight -> R.drawable.ic_wx_clear_night
    WeatherCondition.PartlyCloudyDay -> R.drawable.ic_wx_partly_day
    WeatherCondition.PartlyCloudyNight -> R.drawable.ic_wx_partly_night
    WeatherCondition.Cloudy, WeatherCondition.Unknown -> R.drawable.ic_wx_cloudy
    WeatherCondition.Fog -> R.drawable.ic_wx_fog
    WeatherCondition.Drizzle -> R.drawable.ic_wx_drizzle
    WeatherCondition.Rain -> R.drawable.ic_wx_rain
    WeatherCondition.Snow -> R.drawable.ic_wx_snow
    WeatherCondition.Storm -> R.drawable.ic_wx_storm
}

@StringRes
fun WeatherCondition.labelRes(): Int = when (this) {
    WeatherCondition.ClearDay, WeatherCondition.ClearNight -> R.string.cond_clear
    WeatherCondition.PartlyCloudyDay, WeatherCondition.PartlyCloudyNight -> R.string.cond_partly_cloudy
    WeatherCondition.Cloudy -> R.string.cond_cloudy
    WeatherCondition.Fog -> R.string.cond_fog
    WeatherCondition.Drizzle -> R.string.cond_drizzle
    WeatherCondition.Rain -> R.string.cond_rain
    WeatherCondition.Snow -> R.string.cond_snow
    WeatherCondition.Storm -> R.string.cond_storm
    WeatherCondition.Unknown -> R.string.cond_unknown
}

fun WeatherSnapshot.condition(): WeatherCondition = WeatherCondition.from(weatherCode, isDay)

/** One bit of weather in the pill. [icon] null = text only (condition, high/low). */
data class DetailItem(@DrawableRes val icon: Int?, val text: String)

/** The chosen extras that have data, in a fixed order, for the detail pills. */
fun detailItems(
    context: Context,
    w: WeatherSnapshot,
    s: WidgetSettings,
    nowMs: Long,
    zone: ZoneId,
    use24h: Boolean,
): List<DetailItem> = buildList {
    val f = s.fahrenheit
    for (d in WeatherDetail.entries) {
        if (d !in s.details) continue
        when (d) {
            WeatherDetail.Condition -> add(DetailItem(null, context.getString(w.condition().labelRes())))
            WeatherDetail.HighLow -> add(DetailItem(
                null,
                context.getString(R.string.high_low, formatTemp(w.tempMaxC, f), formatTemp(w.tempMinC, f)),
            ))
            WeatherDetail.SunTimes -> WidgetText.nextSunEvent(w.sunEvents, nowMs)?.let { sun ->
                add(DetailItem(
                    if (sun.sunrise) R.drawable.ic_d_sunrise else R.drawable.ic_d_sunset,
                    WidgetText.clock(sun.atEpochMs, zone, use24h),
                ))
            }
            WeatherDetail.FeelsLike -> w.feelsLikeC?.let {
                add(DetailItem(R.drawable.ic_d_feels, context.getString(R.string.feels_like, formatTemp(it, f))))
            }
            WeatherDetail.RainChance -> w.precipChancePct?.let {
                add(DetailItem(R.drawable.ic_d_rain, context.getString(R.string.percent, it)))
            }
            WeatherDetail.Wind -> w.windKmh?.let { add(DetailItem(R.drawable.ic_d_wind, formatWind(it, f))) }
            WeatherDetail.Humidity -> w.humidityPct?.let {
                add(DetailItem(R.drawable.ic_d_humidity, context.getString(R.string.percent, it)))
            }
            WeatherDetail.Uv -> w.uvIndexMax?.let { add(DetailItem(R.drawable.ic_d_uv, formatUv(it))) }
            WeatherDetail.AirQuality -> (if (f) w.usAqi else w.europeanAqi)?.let {
                add(DetailItem(R.drawable.ic_d_air, context.getString(R.string.aqi, it)))
            }
            WeatherDetail.Pollen -> Pollen.worst(w.pollen)?.let { (reading, level) ->
                // "Pollen low" when it's low; otherwise name the culprit: "Grass high".
                val text = if (level == PollenLevel.Low) context.getString(R.string.pollen_low)
                else context.getString(R.string.pollen_type_level, context.getString(reading.type.labelRes()), context.getString(level.labelRes()))
                add(DetailItem(R.drawable.ic_d_pollen, text))
            }
            WeatherDetail.Moon -> Moon.phase(nowMs).let { add(DetailItem(it.iconRes(), context.getString(it.labelRes()))) }
            WeatherDetail.Tomorrow -> w.tomorrow?.let { t ->
                add(DetailItem(
                    WeatherCondition.from(t.weatherCode, isDay = true).iconRes(),
                    context.getString(R.string.tomorrow_temps, formatTemp(t.maxC, f), formatTemp(t.minC, f)),
                ))
            }
            WeatherDetail.Hourly -> Unit // drawn as its own strip, not an item
        }
    }
}

fun PollenType.labelRes(): Int = when (this) {
    PollenType.Alder -> R.string.pollen_alder
    PollenType.Birch -> R.string.pollen_birch
    PollenType.Grass -> R.string.pollen_grass
    PollenType.Mugwort -> R.string.pollen_mugwort
    PollenType.Olive -> R.string.pollen_olive
    PollenType.Ragweed -> R.string.pollen_ragweed
}

fun PollenLevel.labelRes(): Int = when (this) {
    PollenLevel.Low -> R.string.level_low
    PollenLevel.Moderate -> R.string.level_moderate
    PollenLevel.High -> R.string.level_high
    PollenLevel.VeryHigh -> R.string.level_very_high
}

fun MoonPhase.iconRes(): Int = when (this) {
    MoonPhase.New -> R.drawable.ic_moon_0
    MoonPhase.WaxingCrescent -> R.drawable.ic_moon_1
    MoonPhase.FirstQuarter -> R.drawable.ic_moon_2
    MoonPhase.WaxingGibbous -> R.drawable.ic_moon_3
    MoonPhase.Full -> R.drawable.ic_moon_4
    MoonPhase.WaningGibbous -> R.drawable.ic_moon_5
    MoonPhase.LastQuarter -> R.drawable.ic_moon_6
    MoonPhase.WaningCrescent -> R.drawable.ic_moon_7
}

fun MoonPhase.labelRes(): Int = when (this) {
    MoonPhase.New -> R.string.moon_new
    MoonPhase.WaxingCrescent -> R.string.moon_waxing_crescent
    MoonPhase.FirstQuarter -> R.string.moon_first_quarter
    MoonPhase.WaxingGibbous -> R.string.moon_waxing_gibbous
    MoonPhase.Full -> R.string.moon_full
    MoonPhase.WaningGibbous -> R.string.moon_waning_gibbous
    MoonPhase.LastQuarter -> R.string.moon_last_quarter
    MoonPhase.WaningCrescent -> R.string.moon_waning_crescent
}

@Composable
fun GlassIcon(@DrawableRes res: Int, sizeDp: Int, palette: GlassPalette, description: String? = null) {
    Image(
        provider = ImageProvider(res),
        contentDescription = description,
        colorFilter = ColorFilter.tint(palette.icon),
        modifier = GlanceModifier.size(sizeDp.dp),
    )
}
