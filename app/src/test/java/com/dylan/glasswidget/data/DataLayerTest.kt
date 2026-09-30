package com.dylan.glasswidget.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataLayerTest {

    private val forecastJson = """
        {"latitude":51.5,"longitude":-0.12,"utc_offset_seconds":3600,
         "current_units":{"temperature_2m":"°C"},
         "current":{"time":"2026-09-30T23:45","interval":900,"temperature_2m":13.4,"apparent_temperature":11.8,
                    "weather_code":61,"is_day":0,"relative_humidity_2m":87,"wind_speed_10m":14.6},
         "daily_units":{},
         "daily":{"time":["2026-09-30","2026-10-01"],"temperature_2m_max":[17.2,16.0],"temperature_2m_min":[9.1,8.0],
                  "sunrise":["2026-09-30T06:57","2026-10-01T06:59"],"sunset":["2026-09-30T18:45","2026-10-01T18:43"],
                  "precipitation_probability_max":[80,20],"uv_index_max":[2.45,3.1]}}
    """.trimIndent()

    @Test fun parsesForecast() {
        val s = OpenMeteoApi.parseForecast(forecastJson, nowMs = 42L)
        assertEquals(13.4, s.tempC, 0.0)
        assertEquals(61, s.weatherCode)
        assertEquals(false, s.isDay)
        assertEquals(17.2, s.tempMaxC, 0.0)
        assertEquals(9.1, s.tempMinC, 0.0)
        assertEquals(42L, s.fetchedAtEpochMs)
        assertEquals(11.8, s.feelsLikeC!!, 0.0)
        assertEquals(87, s.humidityPct)
        assertEquals(14.6, s.windKmh!!, 0.0)
        assertEquals(80, s.precipChancePct)
        assertEquals(2.45, s.uvIndexMax!!, 0.0)
        // Local times at UTC+1 -> epoch ms; two days of rise/set, in time order.
        assertEquals(4, s.sunEvents.size)
        assertEquals(java.time.Instant.parse("2026-09-30T05:57:00Z").toEpochMilli(), s.sunEvents[0].atEpochMs)
        assertEquals(true, s.sunEvents[0].sunrise)
        assertEquals(false, s.sunEvents[1].sunrise)
        assertEquals(java.time.Instant.parse("2026-10-01T05:59:00Z").toEpochMilli(), s.sunEvents[2].atEpochMs)
    }

    @Test fun airQualityParsesAndIsMergedButOptional() = runBlocking {
        assertEquals(21.0, OpenMeteoApi.parseAirQuality("""{"current":{"european_aqi":21,"us_aqi":38.6}}""")!!.europeanAqi!!, 0.0)
        val withAir = OpenMeteoApi { url -> if ("air-quality" in url) """{"current":{"european_aqi":21,"us_aqi":38.6}}""" else forecastJson }
        val a = withAir.fetchCurrent(1.0, 2.0, nowMs = 1)
        assertEquals(21, a.europeanAqi)
        assertEquals(39, a.usAqi)
        val airDown = OpenMeteoApi { url -> if ("air-quality" in url) error("503") else forecastJson }
        val b = airDown.fetchCurrent(1.0, 2.0, nowMs = 1)
        assertEquals(null, b.europeanAqi)
        assertEquals(61, b.weatherCode)
    }

    @Test fun forecastWithoutDailyOrIsDayStillParses() {
        val s = OpenMeteoApi.parseForecast(
            """{"current":{"temperature_2m":5.0,"weather_code":3}}""", 1L
        )
        assertEquals(true, s.isDay)
        assertEquals(5.0, s.tempMaxC, 0.0)
        assertEquals(5.0, s.tempMinC, 0.0)
    }

    @Test fun nullDailyEntriesFallBackToCurrent() {
        val s = OpenMeteoApi.parseForecast(
            """{"current":{"temperature_2m":5.0,"weather_code":3},
                "daily":{"temperature_2m_max":[null],"temperature_2m_min":[]}}""", 1L
        )
        assertEquals(5.0, s.tempMaxC, 0.0)
        assertEquals(5.0, s.tempMinC, 0.0)
    }

    @Test fun forecastUrlIsWellFormed() {
        val url = OpenMeteoApi.forecastUrl(-33.86881, 151.20929)
        assertTrue(url, url.contains("latitude=-33.8688&longitude=151.2093"))
        assertTrue(url, url.contains("apparent_temperature") && url.contains("sunrise,sunset"))
        assertTrue(url, url.contains("forecast_days=2"))
        assertTrue(url, url.contains("timezone=auto"))
    }

    @Test fun parsesGeocodingAndBuildsDisplayName() {
        val r = OpenMeteoApi.parseGeocoding(
            """{"results":[
                {"id":1,"name":"Paris","latitude":48.85,"longitude":2.35,"country":"France","admin1":"Île-de-France"},
                {"id":2,"name":"Singapore","latitude":1.29,"longitude":103.85,"country":"Singapore","admin1":"Singapore"}
            ],"generationtime_ms":1.0}"""
        )
        assertEquals(2, r.size)
        assertEquals("Paris, Île-de-France, France", r[0].displayName)
        assertEquals("Singapore", r[1].displayName)
    }

    @Test fun geocodingWithNoResultsIsEmpty() {
        assertEquals(emptyList<GeocodingResult>(), OpenMeteoApi.parseGeocoding("""{"generationtime_ms":0.3}"""))
    }

    @Test fun searchUsesInjectedHttpAndSkipsShortQueries() = runBlocking {
        var calls = 0
        var seenUrl = ""
        val api = OpenMeteoApi { url -> calls++; seenUrl = url; """{"results":[{"name":"São Paulo","latitude":-23.5,"longitude":-46.6}]}""" }
        assertEquals(emptyList<GeocodingResult>(), api.searchCities("a"))
        assertEquals(0, calls)
        val r = api.searchCities("São Paulo")
        assertEquals(1, calls)
        assertTrue(seenUrl, seenUrl.contains("name=S%C3%A3o+Paulo"))
        assertEquals(-23.5, r.single().latitude, 0.0)
    }

    @Test fun fetchCurrentUsesInjectedHttp() = runBlocking {
        val api = OpenMeteoApi { url -> if ("air-quality" in url) "{}" else forecastJson }
        assertEquals(61, api.fetchCurrent(51.5, -0.12, nowMs = 7).weatherCode)
    }

    @Test fun wmoCodesMapToBuckets() {
        val day = true
        assertEquals(WeatherCondition.ClearDay, WeatherCondition.from(0, day))
        assertEquals(WeatherCondition.ClearNight, WeatherCondition.from(0, false))
        assertEquals(WeatherCondition.ClearDay, WeatherCondition.from(1, day))
        assertEquals(WeatherCondition.PartlyCloudyNight, WeatherCondition.from(2, false))
        assertEquals(WeatherCondition.Cloudy, WeatherCondition.from(3, day))
        assertEquals(WeatherCondition.Fog, WeatherCondition.from(48, day))
        assertEquals(WeatherCondition.Drizzle, WeatherCondition.from(55, day))
        assertEquals(WeatherCondition.Rain, WeatherCondition.from(65, day))
        assertEquals(WeatherCondition.Rain, WeatherCondition.from(81, day))
        assertEquals(WeatherCondition.Snow, WeatherCondition.from(73, day))
        assertEquals(WeatherCondition.Snow, WeatherCondition.from(86, day))
        assertEquals(WeatherCondition.Storm, WeatherCondition.from(95, day))
        assertEquals(WeatherCondition.Storm, WeatherCondition.from(99, day))
        assertEquals(WeatherCondition.Unknown, WeatherCondition.from(1234, day))
    }

    @Test fun tempFormatting() {
        assertEquals("21°", formatTemp(21.4, fahrenheit = false))
        assertEquals("22°", formatTemp(21.5, fahrenheit = false))
        assertEquals("0°", formatTemp(-0.3, fahrenheit = false))
        assertEquals("-5°", formatTemp(-5.0, fahrenheit = false))
        assertEquals("68°", formatTemp(20.0, fahrenheit = true))
        assertEquals("32°", formatTemp(0.0, fahrenheit = true))
        assertEquals("-40°", formatTemp(-40.0, fahrenheit = true))
    }

    @Test fun locationKeysRoundToTwoDecimals() {
        assertEquals("51.51,-0.13", LocationKey.of(51.5074, -0.1278))
        assertEquals(Location(51.5074, -0.1278).key, Location(51.5099, -0.1251).key)
    }

    @Test fun storeRoundTripsAndPrunesOldest() {
        fun snap(t: Long) = WeatherSnapshot(1.0, 0, true, 2.0, 0.0, t)
        var store = WeatherStore()
        store = WeatherStoreCodec.merge(store, (1..10).associate { "k$it" to snap(it.toLong()) })
        assertEquals(WeatherStoreCodec.MAX_ENTRIES, store.entries.size)
        assertTrue("k10" in store.entries && "k1" !in store.entries)
        val decoded = WeatherStoreCodec.decode(WeatherStoreCodec.encode(store))
        assertEquals(store, decoded)
    }

    @Test fun corruptCacheDecodesToEmpty() {
        assertEquals(WeatherStore(), WeatherStoreCodec.decode("{not json"))
        assertEquals(WeatherStore(), WeatherStoreCodec.decode(null))
        assertEquals(WeatherStore(), WeatherStoreCodec.decode(""))
    }

    @Test fun optionsFallBackToDefaults() {
        assertEquals(GlassVariant.Soft, GlassVariant.from(null))
        assertEquals(GlassVariant.Clear, GlassVariant.from("clear"))
        assertEquals(TintMode.Frost, TintMode.from("light")) // pre-redesign value falls back cleanly
        assertEquals(TintMode.Ink, TintMode.from("ink"))
        assertEquals(ClockStyle.Glass, ClockStyle.from(null))
        assertEquals(WidgetAlignment.Start, WidgetAlignment.from("start"))
        assertEquals(HourMode.H24, HourMode.from("24"))
        assertEquals(DatePreset.Short, DatePreset.from("nope"))
        assertEquals(TempUnit.F, TempUnit.from("f"))
        assertEquals(LocationMode.Device, LocationMode.from("device"))
    }

    @Test fun weatherDetailsDefaultUntilSaved() {
        assertEquals(WeatherDetail.defaults, WeatherDetail.fromKeys(null))
        assertEquals(emptySet<WeatherDetail>(), WeatherDetail.fromKeys(emptySet()))
        assertEquals(setOf(WeatherDetail.Wind, WeatherDetail.Uv), WeatherDetail.fromKeys(setOf("wind", "uv", "bogus")))
        assertTrue(WeatherDetail.Humidity !in WeatherDetail.defaults)
    }

    @Test fun breakpointsClassifyByHeight() {
        assertEquals(WidgetSize.Compact, WidgetSize.fromHeightDp(70f))
        assertEquals(WidgetSize.Standard, WidgetSize.fromHeightDp(110f))
        assertEquals(WidgetSize.Tall, WidgetSize.fromHeightDp(180f))
        assertEquals(WidgetSize.Large, WidgetSize.fromHeightDp(250f))
    }

    @Test fun unitsAndClamps() {
        assertEquals("15 km/h", formatWind(14.6, imperial = false))
        assertEquals("9 mph", formatWind(14.6, imperial = true))
        assertEquals("UV 3", formatUv(2.5))
        assertEquals(0.8f, Limits.clampTextScale(0.1f), 0f)
        assertEquals(1.3f, Limits.clampTextScale(5f), 0f)
    }

    @Test fun eventsRoundTripAndCorruptIsEmpty() {
        val e = listOf(CalendarEvent(1, "Dentist", 10, 20, false))
        assertEquals(e, WeatherStoreCodec.decodeEvents(WeatherStoreCodec.encodeEvents(e)))
        assertEquals(emptyList<CalendarEvent>(), WeatherStoreCodec.decodeEvents("[{oops"))
    }
}
