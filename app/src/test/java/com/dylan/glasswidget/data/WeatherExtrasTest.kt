package com.dylan.glasswidget.data

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WeatherExtrasTest {
    private val zone = ZoneId.of("Europe/London")
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    @Before fun locale() { Locale.setDefault(Locale.UK) }

    // ---- moon ----

    @Test fun moonPhasesOnKnownDates() {
        assertEquals(MoonPhase.New, Moon.phase(at("2024-01-11T11:57:00Z")))   // new moon
        assertEquals(MoonPhase.Full, Moon.phase(at("2024-01-25T17:54:00Z")))  // full moon
        assertEquals(MoonPhase.FirstQuarter, Moon.phase(at("2024-01-18T03:53:00Z")))
        assertEquals(MoonPhase.LastQuarter, Moon.phase(at("2024-02-02T23:18:00Z")))
    }

    // ---- pollen ----

    @Test fun pollenLevelsAndWorst() {
        assertEquals(PollenLevel.Low, Pollen.level(PollenReading(PollenType.Grass, 10.0)))
        assertEquals(PollenLevel.High, Pollen.level(PollenReading(PollenType.Grass, 60.0)))
        assertEquals(PollenLevel.Moderate, Pollen.level(PollenReading(PollenType.Birch, 60.0)))
        assertEquals(PollenLevel.VeryHigh, Pollen.level(PollenReading(PollenType.Birch, 250.0)))
        val worst = Pollen.worst(listOf(PollenReading(PollenType.Birch, 60.0), PollenReading(PollenType.Grass, 60.0)))!!
        assertEquals(PollenType.Grass, worst.first.type) // grass 60 is High, birch 60 only Moderate
        assertNull(Pollen.worst(emptyList()))
        assertEquals(2, Pollen.fromAirQuality(AirQualityBlock(grass = 3.0, birch = 1.0)).size)
    }

    // ---- warnings ----

    @Test fun warningRegionsForUkCities() {
        fun code(lat: Double, lon: Double) = MetOfficeWarnings.regionFor(lat, lon)?.code
        assertEquals("se", code(51.507, -0.128))   // London
        assertEquals("nw", code(53.48, -2.24))     // Manchester
        assertEquals("yh", code(53.80, -1.55))     // Leeds
        assertEquals("wm", code(52.48, -1.90))     // Birmingham
        assertEquals("sw", code(51.45, -2.59))     // Bristol
        assertEquals("wl", code(51.48, -3.18))     // Cardiff
        assertEquals("st", code(55.86, -4.25))     // Glasgow
        assertEquals("dg", code(55.95, -3.19))     // Edinburgh
        assertEquals("ni", code(54.60, -5.93))     // Belfast
        assertEquals("ee", code(52.63, 1.30))      // Norwich
        assertNull(code(48.86, 2.35))              // Paris
    }

    @Test fun parsesWarningFeed() {
        val rss = """<?xml version="1.0"?><rss><channel><title>Met Office warnings for London &amp; South East England</title>
            <item><title>Yellow warning of rain affecting London &amp; South East England</title><link>x</link></item>
            <item><title><![CDATA[Amber warning of wind affecting London & South East England]]></title></item>
            <item><title>Yellow warning of wind affecting London &amp; South East England</title></item>
            <item><title>Something else</title></item>
            </channel></rss>"""
        val w = MetOfficeWarnings.parseRss(rss)
        assertEquals(listOf(WeatherWarning(WarningLevel.Amber, "wind"), WeatherWarning(WarningLevel.Yellow, "rain")), w)
        assertEquals(emptyList<WeatherWarning>(), MetOfficeWarnings.parseRss("<rss><channel></channel></rss>"))
    }

    // ---- rain outlook ----

    private fun slots(startIso: String, vararg mm: Double) =
        mm.mapIndexed { i, v -> RainSlot(at(startIso) + i * 900_000L, 900_000L, v) }

    @Test fun rainOutlook() {
        val now = at("2026-10-01T13:05:00Z")
        val dryThenWet = slots("2026-10-01T13:00:00Z", 0.0, 0.0, 0.0, 0.2, 0.4)
        assertEquals(RainOutlook.Starting(at("2026-10-01T13:45:00Z")), RainOutlook.from(dryThenWet, now))
        val wetThenDry = slots("2026-10-01T13:00:00Z", 0.3, 0.2, 0.0, 0.0)
        assertEquals(RainOutlook.Easing(at("2026-10-01T13:30:00Z")), RainOutlook.from(wetThenDry, now))
        assertEquals(RainOutlook.Continuing, RainOutlook.from(slots("2026-10-01T13:00:00Z", 0.5, 0.5, 0.5), now))
        assertNull(RainOutlook.from(slots("2026-10-01T13:00:00Z", 0.0, 0.01, 0.0), now))
        // rain more than two hours out isn't mentioned
        assertNull(RainOutlook.from(slots("2026-10-01T15:30:00Z", 1.0), now))
    }

    // ---- smart line ----

    private val labels = SmartLine.Labels(
        now = "Now", today = "Today", tomorrow = "Tomorrow",
        inMinutes = { "In $it min" },
        rainFrom = { "Rain from $it" }, rainEasing = { "Rain easing by $it" }, rainContinuing = "Rain for the next few hours",
        alarm = { "Alarm $it" },
        warning = { level, hazard -> "$level warning: $hazard" },
    )
    private val all = SmartLine.Sources(events = true, rain = true, alarm = true, warnings = true)

    @Test fun smartLinePriorities() {
        val now = at("2026-10-01T09:00:00Z") // 10:00 local
        val soon = CalendarEvent(1, "Standup", at("2026-10-01T09:25:00Z"), at("2026-10-01T09:40:00Z"), false)
        val later = CalendarEvent(2, "Dentist", at("2026-10-01T13:30:00Z"), at("2026-10-01T14:00:00Z"), false)
        val rain = RainOutlook.Starting(at("2026-10-01T10:20:00Z"))
        val alarm = at("2026-10-01T20:00:00Z") // 21:00 local, 11 hours away

        val a = SmartLine.items(now, zone, true, soon, alarm, rain, listOf(WeatherWarning(WarningLevel.Yellow, "wind")), all, labels)
        assertEquals(listOf("In 25 min · Standup", "Rain from 11:20", "Yellow warning: wind", "Alarm 21:00"), a.map { it.text })

        // Amber beats everything; a later event drops below the alarm.
        val b = SmartLine.items(now, zone, true, later, alarm, null, listOf(WeatherWarning(WarningLevel.Amber, "snow")), all, labels)
        assertEquals(listOf("Amber warning: snow", "Alarm 21:00", "14:30 · Dentist"), b.map { it.text })

        // Alarms more than 12 hours away and switched-off sources are left out.
        val c = SmartLine.items(now, zone, true, null, at("2026-10-02T12:00:00Z"), rain, emptyList(),
            all.copy(rain = false), labels)
        assertEquals(emptyList<String>(), c.map { it.text })

        val ongoing = CalendarEvent(3, "Gym", at("2026-10-01T08:30:00Z"), at("2026-10-01T09:30:00Z"), false)
        assertEquals("Now · Gym", SmartLine.items(now, zone, true, ongoing, null, null, emptyList(), all, labels).first().text)
    }

    @Test fun smartLinePicksWhatFits() {
        val items = listOf(
            SmartLine.Item(SmartLine.Kind.Event, "In 25 min · Standup", 1),
            SmartLine.Item(SmartLine.Kind.Rain, "Rain from 11:20", 2),
            SmartLine.Item(SmartLine.Kind.Alarm, "Alarm 7:00", 4),
        )
        assertEquals(2, SmartLine.pick(items, 40).size)
        assertEquals(1, SmartLine.pick(items, 25).size)   // second doesn't fit
        assertEquals(1, SmartLine.pick(items, 5).size)    // the first always shows
        assertTrue(SmartLine.pick(emptyList(), 40).isEmpty())
    }

    // ---- smart line: charging, health, frost, golden hour ----

    private fun snap(
        hourly: List<HourForecast> = emptyList(),
        uvNow: Double? = null,
        aqi: Int? = null,
        pollen: List<PollenReading> = emptyList(),
        sun: List<SunEvent> = emptyList(),
    ) = WeatherSnapshot(
        tempC = 10.0, weatherCode = 0, tempMaxC = 12.0, tempMinC = 2.0, fetchedAtEpochMs = 0,
        hourly = hourly, uvNow = uvNow, europeanAqi = aqi, pollen = pollen, sunEvents = sun,
    )

    @Test fun chargingCountdownToFullOrEighty() {
        val c = SmartLine.Charging(levelPct = 40, fullInMs = 100 * 60_000L)
        assertEquals("Full in 1 h 40 min", SmartLine.chargeText(c, SmartLine.ChargeTarget.Full, labels))
        // 40 of the 60 points left, 80% of that share for the faster first stretch: 100 * 2/3 * 0.8 = 54 min
        assertEquals("80% in 54 min", SmartLine.chargeText(c, SmartLine.ChargeTarget.Eighty, labels))
        assertNull(SmartLine.chargeText(c.copy(levelPct = 85), SmartLine.ChargeTarget.Eighty, labels))
        assertNull(SmartLine.chargeText(c.copy(fullInMs = null), SmartLine.ChargeTarget.Full, labels))
        assertNull(SmartLine.chargeText(c, SmartLine.ChargeTarget.Off, labels))
    }

    @Test fun healthAlertsOnlyWhenBad() {
        val now = at("2026-07-01T11:30:00Z") // 12:30 local
        fun hour(iso: String, uv: Double) = HourForecast(at(iso), 20.0, 0, true, uvIndex = uv)
        val hours = listOf(hour("2026-07-01T12:00:00Z", 7.0), hour("2026-07-01T13:00:00Z", 6.2), hour("2026-07-01T14:00:00Z", 5.0))
        val sources = all.copy(health = true)
        val bad = SmartLine.items(now, zone, true, null, null, null, emptyList(), sources, labels,
            weather = snap(hours, uvNow = 6.8, aqi = 72, pollen = listOf(PollenReading(PollenType.Grass, 80.0))))
        assertEquals(
            listOf("High UV (7) until 15:00", "Poor air quality (72)", "High Grass pollen"),
            bad.map { it.text },
        )
        val fine = SmartLine.items(now, zone, true, null, null, null, emptyList(), sources, labels,
            weather = snap(hours.map { it.copy(uvIndex = 3.0) }, uvNow = 3.0, aqi = 30, pollen = listOf(PollenReading(PollenType.Grass, 35.0))))
        assertTrue(fine.isEmpty())
        // UV that turns high within two hours is flagged ahead
        assertEquals("High UV (7) from 13:00", SmartLine.uvText(snap(hours, uvNow = 4.0), now, labels) { WidgetText.clock(it, zone, true) })
    }

    @Test fun frostAndGoldenHour() {
        fun night(iso: String, t: Double) = HourForecast(at(iso), t, 0, false)
        assertEquals(-1.0, SmartLine.frostLow(snap(listOf(night("2026-11-01T02:00:00Z", 2.0), night("2026-11-01T05:00:00Z", -1.0))))!!, 0.0)
        assertNull(SmartLine.frostLow(snap(listOf(night("2026-11-01T02:00:00Z", 3.0)))))

        val clock = { ms: Long -> WidgetText.clock(ms, zone, true) }
        val sun = listOf(SunEvent(at("2026-10-01T06:00:00Z"), true), SunEvent(at("2026-10-01T17:45:00Z"), false))
        assertEquals("Golden hour · sunset 18:45", SmartLine.goldenText(sun, at("2026-10-01T17:00:00Z"), labels, clock))
        assertEquals("Golden hour until 8:00", SmartLine.goldenText(sun, at("2026-10-01T06:20:00Z"), labels, clock))
        assertNull(SmartLine.goldenText(sun, at("2026-10-01T12:00:00Z"), labels, clock))
    }

    @Test fun weatherItemOrderKeepsSavedOrderAndAddsNewItems() {
        val order = WeatherDetail.order("moon,rain,bogus,rain")
        assertEquals(listOf(WeatherDetail.Moon, WeatherDetail.RainChance), order.take(2))
        assertEquals(WeatherDetail.cardItems.toSet(), order.toSet())
        assertEquals(WeatherDetail.cardItems.size, order.size)
        assertTrue(WeatherDetail.Hourly !in order)
        assertEquals(WeatherDetail.cardItems, WeatherDetail.order(null))
        assertEquals(order, WeatherDetail.order(WeatherDetail.encodeOrder(order)))
    }

    // ---- forecast parsing ----

    @Test fun parsesHourlyRainSlotsAndTomorrow() {
        val json = """{"utc_offset_seconds":3600,
            "current":{"temperature_2m":14.0,"weather_code":2,"is_day":1},
            "daily":{"temperature_2m_max":[17.0,15.0],"temperature_2m_min":[9.0,7.0],"weather_code":[2,61],
                     "precipitation_probability_max":[20,70]},
            "hourly":{"time":["2026-10-01T10:00","2026-10-01T11:00","2026-10-01T12:00"],
                      "temperature_2m":[14.0,15.0,16.0],"weather_code":[2,3,61],"is_day":[1,1,1],
                      "precipitation_probability":[5,10,60],"precipitation":[0.0,0.0,1.2]},
            "minutely_15":{"time":["2026-10-01T10:15","2026-10-01T10:30","2026-10-01T10:45"],"precipitation":[0.0,0.1,0.3]}}"""
        val now = at("2026-10-01T09:20:00Z") // 10:20 local
        val s = OpenMeteoApi.parseForecast(json, now)
        assertEquals(listOf(15.0, 16.0), s.hourly.map { it.tempC })          // from the coming hour on
        assertEquals(60, s.hourly.last().precipChancePct)
        assertEquals(3, s.rainSlots.size)                                    // 15-minute slots preferred
        assertEquals(900_000L, s.rainSlots.first().lengthMs)
        assertEquals(DayForecast(15.0, 7.0, 61, 70), s.tomorrow)
    }
}
