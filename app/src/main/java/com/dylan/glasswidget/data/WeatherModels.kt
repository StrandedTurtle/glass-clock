package com.dylan.glasswidget.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- Open-Meteo forecast response (only the bits we use) ----

@Serializable
data class OpenMeteoResponse(
    val current: CurrentBlock,
    val daily: DailyBlock? = null,
)

@Serializable
data class CurrentBlock(
    @SerialName("temperature_2m") val temperature2m: Double,
    @SerialName("weather_code") val weatherCode: Int,
    // 1 = daytime, 0 = night. Defaulted so a response without it still parses.
    @SerialName("is_day") val isDay: Int = 1,
)

@Serializable
data class DailyBlock(
    @SerialName("temperature_2m_max") val tempMax: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val tempMin: List<Double?> = emptyList(),
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
data class WeatherSnapshot(
    val tempC: Double,
    val weatherCode: Int,
    val isDay: Boolean = true,
    val tempMaxC: Double,
    val tempMinC: Double,
    val fetchedAtEpochMs: Long,
)

/** Everything cached, keyed by [LocationKey] so several widgets can show different cities. */
@Serializable
data class WeatherStore(val entries: Map<String, WeatherSnapshot> = emptyMap())
