package com.dylan.glasswidget.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- Open-Meteo forecast response (only the bits we use) ----

@Serializable
data class OpenMeteoResponse(
    val current: CurrentBlock,
    val daily: DailyBlock? = null,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
)

@Serializable
data class CurrentBlock(
    @SerialName("temperature_2m") val temperature2m: Double,
    @SerialName("apparent_temperature") val apparentTemperature: Double? = null,
    @SerialName("weather_code") val weatherCode: Int,
    // 1 = daytime, 0 = night. Defaulted so a response without it still parses.
    @SerialName("is_day") val isDay: Int = 1,
    @SerialName("relative_humidity_2m") val humidity: Double? = null,
    @SerialName("wind_speed_10m") val windSpeedKmh: Double? = null,
)

@Serializable
data class DailyBlock(
    @SerialName("temperature_2m_max") val tempMax: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val tempMin: List<Double?> = emptyList(),
    // Local wall-clock times at the location, e.g. "2026-10-01T06:59".
    val sunrise: List<String?> = emptyList(),
    val sunset: List<String?> = emptyList(),
    @SerialName("precipitation_probability_max") val precipProbMax: List<Double?> = emptyList(),
    @SerialName("uv_index_max") val uvIndexMax: List<Double?> = emptyList(),
)

// ---- Open-Meteo air quality response ----

@Serializable
data class AirQualityResponse(val current: AirQualityBlock? = null)

@Serializable
data class AirQualityBlock(
    @SerialName("european_aqi") val europeanAqi: Double? = null,
    @SerialName("us_aqi") val usAqi: Double? = null,
)

// ---- Open-Meteo geocoding response ----

@Serializable
data class GeocodingResponse(val results: List<GeocodingResult> = emptyList())

@Serializable
data class GeocodingResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    @SerialName("admin1") val region: String? = null,
) {
    /** "Paris, Île-de-France, France" — skips blanks and repeated parts. */
    val displayName: String
        get() = listOfNotNull(name, region, country)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
}

// ---- What we cache and render ----

@Serializable
data class SunEvent(val atEpochMs: Long, val sunrise: Boolean)

@Serializable
data class WeatherSnapshot(
    val tempC: Double,
    val weatherCode: Int,
    val isDay: Boolean = true,
    val tempMaxC: Double,
    val tempMinC: Double,
    val fetchedAtEpochMs: Long,
    val feelsLikeC: Double? = null,
    val humidityPct: Int? = null,
    val windKmh: Double? = null,
    val precipChancePct: Int? = null,
    val uvIndexMax: Double? = null,
    val europeanAqi: Int? = null,
    val usAqi: Int? = null,
    /** Today's and tomorrow's sunrise/sunset, oldest first. */
    val sunEvents: List<SunEvent> = emptyList(),
)

/** Everything cached, keyed by [LocationKey] so several widgets can show different cities. */
@Serializable
data class WeatherStore(val entries: Map<String, WeatherSnapshot> = emptyMap())

// ---- Calendar ----

@Serializable
data class CalendarEvent(
    val id: Long,
    val title: String,
    val beginEpochMs: Long,
    val endEpochMs: Long,
    val allDay: Boolean,
)
