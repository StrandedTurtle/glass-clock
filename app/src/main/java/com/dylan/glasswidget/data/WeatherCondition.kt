package com.dylan.glasswidget.data

/** Icon/text bucket for a WMO weather code. */
enum class WeatherCondition {
    ClearDay, ClearNight, PartlyCloudyDay, PartlyCloudyNight, Cloudy,
    Fog, Drizzle, Rain, Snow, Storm, Unknown;

    companion object {
        fun from(wmoCode: Int, isDay: Boolean): WeatherCondition = when (wmoCode) {
            0, 1 -> if (isDay) ClearDay else ClearNight
            2 -> if (isDay) PartlyCloudyDay else PartlyCloudyNight
            3 -> Cloudy
            45, 48 -> Fog
            in 51..57 -> Drizzle
            in 61..67 -> Rain
            in 71..77 -> Snow
            in 80..82 -> Rain
            85, 86 -> Snow
            95, 96, 99 -> Storm
            else -> Unknown
        }
    }
}
