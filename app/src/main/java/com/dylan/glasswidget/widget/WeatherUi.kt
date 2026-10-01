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

data class DetailItem(@DrawableRes val icon: Int, val text: String)

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
            else -> Unit // condition and high/low sit next to the temperature
        }
    }
}

/** "Cloudy · ↑17° ↓9°" — whichever of the two in-pill extras are switched on. */
fun pillExtras(context: Context, w: WeatherSnapshot, s: WidgetSettings, roomForCondition: Boolean): String =
    listOfNotNull(
        if (WeatherDetail.Condition in s.details && roomForCondition) context.getString(w.condition().labelRes()) else null,
        if (WeatherDetail.HighLow in s.details)
            context.getString(R.string.high_low, formatTemp(w.tempMaxC, s.fahrenheit), formatTemp(w.tempMinC, s.fahrenheit))
        else null,
    ).joinToString(" · ")

@Composable
fun GlassIcon(@DrawableRes res: Int, sizeDp: Int, palette: GlassPalette, description: String? = null) {
    Image(
        provider = ImageProvider(res),
        contentDescription = description,
        colorFilter = ColorFilter.tint(palette.icon),
        modifier = GlanceModifier.size(sizeDp.dp),
    )
}
