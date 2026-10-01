package com.dylan.glasswidget.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- Open-Meteo forecast response (only the bits we use) ----

@Serializable
data class OpenMeteoResponse(
    val current: CurrentBlock,
    val daily: DailyBlock? = null,
    val hourly: HourlyBlock? = null,
    @SerialName("minutely_15") val minutely15: Minutely15Block? = null,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
)

@Serializable
data class HourlyBlock(
    // Local wall-clock hours at the location, e.g. "2026-10-01T10:00".
    val time: List<String> = emptyList(),
    @SerialName("precipitation_probability") val precipProb: List<Double?> = emptyList(),
    @SerialName("temperature_2m") val temperature: List<Double?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("is_day") val isDay: List<Int?> = emptyList(),
    val precipitation: List<Double?> = emptyList(), // mm in the hour
    @SerialName("uv_index") val uvIndex: List<Double?> = emptyList(),
)

@Serializable
data class Minutely15Block(
    val time: List<String> = emptyList(),
    val precipitation: List<Double?> = emptyList(), // mm in the 15 minutes
)

@Serializable
data class CurrentBlock(
    // Nullable: a single model (e.g. the Met Office's) can leave a field out; we fall back per field.
    @SerialName("temperature_2m") val temperature2m: Double? = null,
    @SerialName("apparent_temperature") val apparentTemperature: Double? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
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
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
)

// ---- Open-Meteo air quality response ----

@Serializable
data class AirQualityResponse(val current: AirQualityBlock? = null)

@Serializable
data class AirQualityBlock(
    @SerialName("european_aqi") val europeanAqi: Double? = null,
    @SerialName("us_aqi") val usAqi: Double? = null,
    // Grains per m³ (CAMS, Europe only).
    @SerialName("alder_pollen") val alder: Double? = null,
    @SerialName("birch_pollen") val birch: Double? = null,
    @SerialName("grass_pollen") val grass: Double? = null,
    @SerialName("mugwort_pollen") val mugwort: Double? = null,
    @SerialName("olive_pollen") val olive: Double? = null,
    @SerialName("ragweed_pollen") val ragweed: Double? = null,
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

/** One hour of forecast, for the hourly strip. */
@Serializable
data class HourForecast(
    val atEpochMs: Long,
    val tempC: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val precipChancePct: Int? = null,
    val uvIndex: Double? = null,
)

/** Rain amount in one short slot (15 minutes, or an hour when finer data isn't there). */
@Serializable
data class RainSlot(val atEpochMs: Long, val lengthMs: Long, val mm: Double)

/** Tomorrow at a glance. */
@Serializable
data class DayForecast(val maxC: Double, val minC: Double, val weatherCode: Int, val precipChancePct: Int? = null)

/** A pollen count (grains/m³) for one type. */
@Serializable
data class PollenReading(val type: PollenType, val grains: Double)

/** An active Met Office weather warning. */
@Serializable
data class WeatherWarning(val level: WarningLevel, val hazard: String)

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
    /** UV index for the hour in progress. */
    val uvNow: Double? = null,
    val europeanAqi: Int? = null,
    val usAqi: Int? = null,
    /** Today's and tomorrow's sunrise/sunset, oldest first. */
    val sunEvents: List<SunEvent> = emptyList(),
    /** True when temperatures and conditions came from the UK Met Office models. */
    val metOffice: Boolean = false,
    val hourly: List<HourForecast> = emptyList(),
    val rainSlots: List<RainSlot> = emptyList(),
    val tomorrow: DayForecast? = null,
    val pollen: List<PollenReading> = emptyList(),
    val warnings: List<WeatherWarning> = emptyList(),
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
    /** [DEVICE_SOURCE] for Android's calendar, or [Ics.sourceKey] of the calendar link it came from. */
    val source: String = DEVICE_SOURCE,
    /** Where it is, if the event says (tapping it then opens Maps). */
    val location: String? = null,
) {
    val fromLink: Boolean get() = source != DEVICE_SOURCE

    companion object {
        const val DEVICE_SOURCE = "device"
    }
}
