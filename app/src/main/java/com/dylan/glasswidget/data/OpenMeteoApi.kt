package com.dylan.glasswidget.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Tiny Open-Meteo client on plain HttpURLConnection (no HTTP library needed for a few GETs).
 * [http] is injectable so the parsing can be unit-tested without a network.
 */
class OpenMeteoApi(private val http: (url: String) -> String = ::httpGet) {

    /** Forecast plus air quality. Air quality is best-effort: if that call fails the weather still lands. */
    suspend fun fetchCurrent(lat: Double, lon: Double, nowMs: Long = System.currentTimeMillis()): WeatherSnapshot =
        withContext(Dispatchers.IO) {
            val weather = parseForecast(http(forecastUrl(lat, lon)), nowMs)
            val air = runCatching { parseAirQuality(http(airQualityUrl(lat, lon))) }.getOrNull()
            weather.copy(
                europeanAqi = air?.europeanAqi?.roundToInt(),
                usAqi = air?.usAqi?.roundToInt(),
            )
        }

    suspend fun searchCities(query: String, count: Int = 8): List<GeocodingResult> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        return withContext(Dispatchers.IO) { parseGeocoding(http(geocodingUrl(q, count))) }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        fun forecastUrl(lat: Double, lon: Double): String =
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${coord(lat)}&longitude=${coord(lon)}" +
                "&current=temperature_2m,apparent_temperature,weather_code,is_day,relative_humidity_2m,wind_speed_10m" +
                "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max,uv_index_max" +
                "&forecast_days=2&timezone=auto"

        fun airQualityUrl(lat: Double, lon: Double): String =
            "https://air-quality-api.open-meteo.com/v1/air-quality" +
                "?latitude=${coord(lat)}&longitude=${coord(lon)}&current=european_aqi,us_aqi"

        fun geocodingUrl(query: String, count: Int): String =
            "https://geocoding-api.open-meteo.com/v1/search" +
                "?name=${URLEncoder.encode(query, "UTF-8")}&count=$count&language=en&format=json"

        private fun coord(v: Double) = String.format(Locale.US, "%.4f", v)

        fun parseForecast(body: String, nowMs: Long): WeatherSnapshot {
            val resp = json.decodeFromString<OpenMeteoResponse>(body)
            val now = resp.current.temperature2m
            val daily = resp.daily
            val offset = ZoneOffset.ofTotalSeconds(resp.utcOffsetSeconds)
            val sun = buildList {
                val days = maxOf(daily?.sunrise?.size ?: 0, daily?.sunset?.size ?: 0)
                for (i in 0 until days) {
                    daily?.sunrise?.getOrNull(i)?.let { localToEpochMs(it, offset) }?.let { add(SunEvent(it, true)) }
                    daily?.sunset?.getOrNull(i)?.let { localToEpochMs(it, offset) }?.let { add(SunEvent(it, false)) }
                }
            }.sortedBy { it.atEpochMs }
            return WeatherSnapshot(
                tempC = now,
                weatherCode = resp.current.weatherCode,
                isDay = resp.current.isDay != 0,
                tempMaxC = daily?.tempMax?.firstOrNull() ?: now,
                tempMinC = daily?.tempMin?.firstOrNull() ?: now,
                fetchedAtEpochMs = nowMs,
                feelsLikeC = resp.current.apparentTemperature,
                humidityPct = resp.current.humidity?.roundToInt(),
                windKmh = resp.current.windSpeedKmh,
                precipChancePct = daily?.precipProbMax?.firstOrNull()?.roundToInt(),
                uvIndexMax = daily?.uvIndexMax?.firstOrNull(),
                sunEvents = sun,
            )
        }

        fun parseAirQuality(body: String): AirQualityBlock? =
            json.decodeFromString<AirQualityResponse>(body).current

        fun parseGeocoding(body: String): List<GeocodingResult> =
            json.decodeFromString<GeocodingResponse>(body).results

        /** "2026-10-01T06:59" at the location's UTC offset -> epoch ms; null if it doesn't parse. */
        private fun localToEpochMs(local: String, offset: ZoneOffset): Long? =
            runCatching { LocalDateTime.parse(local).toInstant(offset).toEpochMilli() }.getOrNull()
    }
}

/** Blocking GET returning the body; throws on any non-200. Call off the main thread. */
fun httpGet(url: String): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "GlassClockWidget/1.0")
        val code = conn.responseCode
        if (code != HttpURLConnection.HTTP_OK) error("HTTP $code for $url")
        return conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}
