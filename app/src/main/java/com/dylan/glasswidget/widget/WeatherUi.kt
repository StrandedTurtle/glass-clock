package com.dylan.glasswidget.widget

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.size
import androidx.glance.layout.width
import com.dylan.glasswidget.R
import com.dylan.glasswidget.data.TempUnit
import com.dylan.glasswidget.data.WeatherCondition
import com.dylan.glasswidget.data.WeatherSnapshot
import com.dylan.glasswidget.data.formatTemp

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

/** Icon + current temperature on one line. A null [weather] shows [placeholder] until the first fetch lands. */
@Composable
fun WeatherRow(
    context: Context,
    weather: WeatherSnapshot?,
    unit: TempUnit,
    iconDp: Int,
    textSp: Float,
    palette: GlassPalette,
    placeholder: String,
) {
    val f = unit == TempUnit.F
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider((weather?.condition() ?: WeatherCondition.Unknown).iconRes()),
            contentDescription = weather?.let { context.getString(it.condition().labelRes()) },
            colorFilter = ColorFilter.tint(palette.text),
            modifier = GlanceModifier.size(iconDp.dp),
        )
        Spacer(GlanceModifier.width(5.dp))
        LabelText(
            context = context,
            text = weather?.let { formatTemp(it.tempC, f) } ?: placeholder,
            sizeSp = textSp,
            palette = palette,
        )
    }
}
