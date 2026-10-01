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

    /**
     * Temperatures and conditions from the UK Met Office models (UKV 2 km over the UK and Ireland, its
     * 10 km global model elsewhere), filled in per field from Open-Meteo's default blend, which also
     * provides the rain chance (an ensemble forecast; the Met Office feed has none). If the Met Office
     * request fails, the default blend alone is used. Air quality is best-effort on top.
     */
    suspend fun fetchCurrent(lat: Double, lon: Double, nowMs: Long = System.currentTimeMillis()): WeatherSnapshot =
        withContext(Dispatchers.IO) {
            val blend = parseForecast(http(forecastUrl(lat, lon)), nowMs)
            val metOffice = runCatching { parseForecast(http(forecastUrl(lat, lon, MET_OFFICE)), nowMs) }.getOrNull()
            val weather = metOffice?.let { preferring(it, blend) } ?: blend
            val air = runCatching { parseAirQuality(http(airQualityUrl(lat, lon))) }.getOrNull()
            // UK only: active Met Office warnings for the region this location is in.
            val warnings = MetOfficeWarnings.regionFor(lat, lon)?.let { region ->
                runCatching { MetOfficeWarnings.parseRss(http(MetOfficeWarnings.feedUrl(region.code))) }.getOrNull()
            }.orEmpty()
            weather.copy(
                europeanAqi = air?.europeanAqi?.roundToInt(),
                usAqi = air?.usAqi?.roundToInt(),
                pollen = Pollen.fromAirQuality(air),
                warnings = warnings,
            )
        }

    suspend fun searchCities(query: String, count: Int = 8): List<GeocodingResult> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        return withContext(Dispatchers.IO) { parseGeocoding(http(geocodingUrl(q, count))) }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        /** Open-Meteo's name for the Met Office models: UKV 2 km for the UK/Ireland, global 10 km elsewhere. */
        const val MET_OFFICE = "ukmo_seamless"

        /** How far ahead the rain chance looks: "will it rain while I'm out". */
        const val RAIN_WINDOW_HOURS = 3

        fun forecastUrl(lat: Double, lon: Double, model: String? = null): String =
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${coord(lat)}&longitude=${coord(lon)}" +
                "&current=temperature_2m,apparent_temperature,weather_code,is_day,relative_humidity_2m,wind_speed_10m" +
                "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max,uv_index_max,weather_code" +
                "&hourly=precipitation_probability,temperature_2m,weather_code,is_day,precipitation,uv_index" +
                "&minutely_15=precipitation&forecast_minutely_15=16" +
                "&forecast_days=2&timezone=auto" +
                (model?.let { "&models=$it" } ?: "")

        /** [primary]'s values where it has them, otherwise [fallback]'s; rain chance always from the ensemble blend. */
        fun preferring(primary: WeatherSnapshot, fallback: WeatherSnapshot): WeatherSnapshot {
            val fromBlend = fallback.hourly.associateBy { it.atEpochMs }
            val hourly = primary.hourly.ifEmpty { fallback.hourly }.map { h ->
                val b = fromBlend[h.atEpochMs]
                h.copy(precipChancePct = b?.precipChancePct, uvIndex = h.uvIndex ?: b?.uvIndex)
            }
            return primary.copy(
                feelsLikeC = primary.feelsLikeC ?: fallback.feelsLikeC,
                humidityPct = primary.humidityPct ?: fallback.humidityPct,
                windKmh = primary.windKmh ?: fallback.windKmh,
                precipChancePct = fallback.precipChancePct,
                uvIndexMax = primary.uvIndexMax ?: fallback.uvIndexMax,
                uvNow = primary.uvNow ?: fallback.uvNow,
                sunEvents = primary.sunEvents.ifEmpty { fallback.sunEvents },
                hourly = hourly,
                rainSlots = primary.rainSlots.ifEmpty { fallback.rainSlots },
                tomorrow = (primary.tomorrow ?: fallback.tomorrow)?.copy(precipChancePct = fallback.tomorrow?.precipChancePct),
                metOffice = true,
            )
        }

        /** How many hours ahead the hourly strip can draw from. */
        const val HOURLY_AHEAD = 12

        fun airQualityUrl(lat: Double, lon: Double): String =
            "https://air-quality-api.open-meteo.com/v1/air-quality" +
                "?latitude=${coord(lat)}&longitude=${coord(lon)}" +
                "&current=european_aqi,us_aqi,alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen"

        fun geocodingUrl(query: String, count: Int): String =
            "https://geocoding-api.open-meteo.com/v1/search" +
                "?name=${URLEncoder.encode(query, "UTF-8")}&count=$count&language=en&format=json"

        private fun coord(v: Double) = String.format(Locale.US, "%.4f", v)

        fun parseForecast(body: String, nowMs: Long): WeatherSnapshot {
            val resp = json.decodeFromString<OpenMeteoResponse>(body)
            val now = resp.current.temperature2m ?: error("Forecast has no current temperature")
            val code = resp.current.weatherCode ?: error("Forecast has no current conditions")
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
                weatherCode = code,
                isDay = resp.current.isDay != 0,
                tempMaxC = daily?.tempMax?.firstOrNull() ?: now,
                tempMinC = daily?.tempMin?.firstOrNull() ?: now,
                fetchedAtEpochMs = nowMs,
                feelsLikeC = resp.current.apparentTemperature,
                humidityPct = resp.current.humidity?.roundToInt(),
                windKmh = resp.current.windSpeedKmh,
                // Highest hourly chance over the next few hours; the day's maximum only if there's no hourly data.
                precipChancePct = rainChanceSoon(resp.hourly, offset, nowMs)
                    ?: daily?.precipProbMax?.firstOrNull()?.roundToInt(),
                uvIndexMax = daily?.uvIndexMax?.firstOrNull(),
                uvNow = uvNow(resp.hourly, offset, nowMs),
                sunEvents = sun,
                hourly = hours(resp.hourly, offset, nowMs),
                rainSlots = rainSlots(resp.minutely15, resp.hourly, offset, nowMs),
                tomorrow = tomorrow(daily),
            )
        }

        /** The next [HOURLY_AHEAD] whole hours (from the coming hour), for the hourly strip. */
        private fun hours(h: HourlyBlock?, offset: ZoneOffset, nowMs: Long): List<HourForecast> {
            if (h == null) return emptyList()
            return h.time.indices.mapNotNull { i ->
                val at = localToEpochMs(h.time[i], offset) ?: return@mapNotNull null
                if (at <= nowMs) return@mapNotNull null
                val temp = h.temperature.getOrNull(i) ?: return@mapNotNull null
                val code = h.weatherCode.getOrNull(i) ?: return@mapNotNull null
                HourForecast(
                    at, temp, code, (h.isDay.getOrNull(i) ?: 1) != 0,
                    h.precipProb.getOrNull(i)?.roundToInt(), h.uvIndex.getOrNull(i),
                )
            }.take(HOURLY_AHEAD)
        }

        /** UV index for the hour in progress. */
        private fun uvNow(h: HourlyBlock?, offset: ZoneOffset, nowMs: Long): Double? {
            if (h == null) return null
            val i = h.time.indexOfFirst { t ->
                val start = localToEpochMs(t, offset) ?: return@indexOfFirst false
                nowMs >= start && nowMs < start + 3_600_000L
            }
            return if (i < 0) null else h.uvIndex.getOrNull(i)
        }

        /** Rain amounts ahead: 15-minute slots where the model has them, else hourly. */
        private fun rainSlots(m: Minutely15Block?, h: HourlyBlock?, offset: ZoneOffset, nowMs: Long): List<RainSlot> {
            fun slots(times: List<String>, mm: List<Double?>, length: Long) = times.indices.mapNotNull { i ->
                val at = localToEpochMs(times[i], offset) ?: return@mapNotNull null
                val amount = mm.getOrNull(i) ?: return@mapNotNull null
                if (at + length <= nowMs || at > nowMs + 4 * 3_600_000L) null else RainSlot(at, length, amount)
            }
            val fine = if (m != null) slots(m.time, m.precipitation, 15 * 60_000L) else emptyList()
            return fine.ifEmpty { if (h != null) slots(h.time, h.precipitation, 3_600_000L) else emptyList() }
        }

        private fun tomorrow(d: DailyBlock?): DayForecast? {
            if (d == null) return null
            val max = d.tempMax.getOrNull(1) ?: return null
            val min = d.tempMin.getOrNull(1) ?: return null
            val code = d.weatherCode.getOrNull(1) ?: return null
            return DayForecast(max, min, code, d.precipProbMax.getOrNull(1)?.roundToInt())
        }

        /** Max hourly precipitation probability for the hour in progress and the next [RAIN_WINDOW_HOURS]. */
        fun rainChanceSoon(hourly: HourlyBlock?, offset: ZoneOffset, nowMs: Long): Int? {
            if (hourly == null) return null
            val hourMs = 3_600_000L
            return hourly.time.indices.mapNotNull { i ->
                val start = localToEpochMs(hourly.time[i], offset) ?: return@mapNotNull null
                if (start + hourMs > nowMs && start < nowMs + RAIN_WINDOW_HOURS * hourMs) hourly.precipProb.getOrNull(i) else null
            }.maxOrNull()?.roundToInt()
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
