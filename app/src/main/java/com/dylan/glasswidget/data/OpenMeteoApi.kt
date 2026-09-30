package com.dylan.glasswidget.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Tiny Open-Meteo client on plain HttpURLConnection (no HTTP library needed for two GETs).
 * [http] is injectable so the parsing can be unit-tested without a network.
 */
class OpenMeteoApi(private val http: (url: String) -> String = ::httpGet) {

    suspend fun fetchCurrent(lat: Double, lon: Double, nowMs: Long = System.currentTimeMillis()): WeatherSnapshot =
        withContext(Dispatchers.IO) { parseForecast(http(forecastUrl(lat, lon)), nowMs) }

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
                "&current=temperature_2m,weather_code,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min" +
                "&forecast_days=1&timezone=auto"

        fun geocodingUrl(query: String, count: Int): String =
            "https://geocoding-api.open-meteo.com/v1/search" +
                "?name=${URLEncoder.encode(query, "UTF-8")}&count=$count&language=en&format=json"

        private fun coord(v: Double) = String.format(Locale.US, "%.4f", v)

        fun parseForecast(body: String, nowMs: Long): WeatherSnapshot {
            val resp = json.decodeFromString<OpenMeteoResponse>(body)
            val now = resp.current.temperature2m
            return WeatherSnapshot(
                tempC = now,
                weatherCode = resp.current.weatherCode,
                isDay = resp.current.isDay != 0,
                tempMaxC = resp.daily?.tempMax?.firstOrNull() ?: now,
                tempMinC = resp.daily?.tempMin?.firstOrNull() ?: now,
                fetchedAtEpochMs = nowMs,
            )
        }

        fun parseGeocoding(body: String): List<GeocodingResult> =
            json.decodeFromString<GeocodingResponse>(body).results
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
