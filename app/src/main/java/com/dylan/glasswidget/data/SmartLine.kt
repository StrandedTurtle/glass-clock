package com.dylan.glasswidget.data

import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The line above the clock: the most useful thing right now, like the lockscreen's top line. Candidates
 * are ranked; the widget shows the top one or two that fit. Pure (clock, zone and wording passed in).
 */
object SmartLine {

    enum class Kind { Warning, Event, Rain, Charging, Health, Frost, Sun, Alarm }

    data class Item(val kind: Kind, val text: String, val priority: Int)

    /** Wording, supplied from string resources. */
    class Labels(
        val now: String,
        val today: String,
        val tomorrow: String,
        val inMinutes: (Int) -> String,          // "In 25 min"
        val rainFrom: (String) -> String,        // "Rain from 14:20"
        val rainEasing: (String) -> String,      // "Rain easing by 15:00"
        val rainContinuing: String,              // "Rain for the next few hours"
        val alarm: (String) -> String,           // "Alarm 07:00"
        val warning: (WarningLevel, String) -> String, // "Yellow warning: wind"
        val chargeFull: (String) -> String = { "Full in $it" },
        val chargeTo80: (String) -> String = { "80% in $it" },
        val duration: (Int) -> String = { m -> if (m < 60) "$m min" else "${m / 60} h ${m % 60} min" },
        val uvHighUntil: (Int, String) -> String = { uv, t -> "High UV ($uv) until $t" },
        val uvHighFrom: (Int, String) -> String = { uv, t -> "High UV ($uv) from $t" },
        val airPoor: (Int) -> String = { "Poor air quality ($it)" },
        val pollenHigh: (PollenType, PollenLevel) -> String = { t, l -> "$l $t pollen" },
        val goldenEvening: (String) -> String = { "Golden hour · sunset $it" },
        val goldenMorning: (String) -> String = { "Golden hour until $it" },
        val frost: (String) -> String = { "Frost likely · low $it" },
        val temp: (Double) -> String = { "${it.roundToInt()}°" },
    )

    /** Charge target for the charging countdown. */
    enum class ChargeTarget(val key: String) {
        Off("off"), Full("full"), Eighty("80");

        companion object {
            fun from(key: String?) = entries.firstOrNull { it.key == key } ?: Full
        }
    }

    data class Sources(
        val events: Boolean,
        val rain: Boolean,
        val alarm: Boolean,
        val warnings: Boolean,
        val charge: ChargeTarget = ChargeTarget.Off,
        val health: Boolean = false,  // high UV, poor air, high pollen
        val sun: Boolean = false,     // golden hour
        val frost: Boolean = false,
    )

    /** Battery state, when plugged in. [fullInMs] is Android's estimate to 100%, null if unknown. */
    data class Charging(val levelPct: Int, val fullInMs: Long?)

    const val COUNTDOWN_MS = 60 * 60_000L
    const val ALARM_AHEAD_MS = 12 * 3_600_000L
    const val GOLDEN_HOUR_MS = 60 * 60_000L
    const val UV_HIGH = 6.0
    const val EU_AQI_POOR = 60
    const val US_AQI_POOR = 101
    const val FROST_C = 1.0

    fun items(
        nowMs: Long,
        zone: ZoneId,
        use24h: Boolean,
        event: CalendarEvent?,
        alarmMs: Long?,
        rain: RainOutlook?,
        warnings: List<WeatherWarning>,
        sources: Sources,
        labels: Labels,
        weather: WeatherSnapshot? = null,
        charging: Charging? = null,
        usAqi: Boolean = false,
    ): List<Item> = buildList {
        fun clock(ms: Long) = WidgetText.clock(ms, zone, use24h)

        if (sources.warnings) warnings.maxByOrNull { it.level }?.let { w ->
            add(Item(Kind.Warning, labels.warning(w.level, w.hazard), if (w.level == WarningLevel.Yellow) 4 else 0))
        }
        if (sources.events && event != null) {
            val title = event.title
            val soon = !event.allDay && event.beginEpochMs - nowMs in 1..COUNTDOWN_MS
            val now = !event.allDay && event.beginEpochMs <= nowMs && event.endEpochMs > nowMs
            val whenText = when {
                now -> labels.now
                soon -> labels.inMinutes(ceil((event.beginEpochMs - nowMs) / 60_000.0).toInt())
                else -> WidgetText.eventWhen(event, nowMs, zone, use24h, labels.now, labels.today, labels.tomorrow)
            }
            add(Item(Kind.Event, "$whenText · $title", if (now || soon) 1 else 8))
        }
        if (sources.rain && rain != null) {
            val text = when (rain) {
                is RainOutlook.Starting -> labels.rainFrom(clock(rain.atMs))
                is RainOutlook.Easing -> labels.rainEasing(clock(rain.atMs))
                RainOutlook.Continuing -> labels.rainContinuing
            }
            add(Item(Kind.Rain, text, 2))
        }
        if (charging != null) chargeText(charging, sources.charge, labels)?.let { add(Item(Kind.Charging, it, 3)) }
        if (sources.health && weather != null) {
            uvText(weather, nowMs, labels, ::clock)?.let { add(Item(Kind.Health, it, 5)) }
            val aqi = if (usAqi) weather.usAqi else weather.europeanAqi
            if (aqi != null && aqi >= (if (usAqi) US_AQI_POOR else EU_AQI_POOR)) add(Item(Kind.Health, labels.airPoor(aqi), 5))
            Pollen.worst(weather.pollen)?.let { (reading, level) ->
                if (level >= PollenLevel.High) add(Item(Kind.Health, labels.pollenHigh(reading.type, level), 5))
            }
        }
        if (sources.frost && weather != null) frostLow(weather)?.let { add(Item(Kind.Frost, labels.frost(labels.temp(it)), 6)) }
        if (sources.sun && weather != null) goldenText(weather.sunEvents, nowMs, labels, ::clock)?.let { add(Item(Kind.Sun, it, 6)) }
        if (sources.alarm && alarmMs != null && alarmMs - nowMs in 1..ALARM_AHEAD_MS) {
            add(Item(Kind.Alarm, labels.alarm(clock(alarmMs)), 7))
        }
    }.sortedBy { it.priority }

    /** "Full in 1 h 5 min" / "80% in 20 min"; null when off, unknown, or already there. */
    fun chargeText(c: Charging, target: ChargeTarget, labels: Labels): String? {
        val full = c.fullInMs ?: return null
        if (full <= 0 || c.levelPct >= 100) return null
        return when (target) {
            ChargeTarget.Off -> null
            ChargeTarget.Full -> labels.chargeFull(labels.duration(minutes(full)))
            ChargeTarget.Eighty -> {
                if (c.levelPct >= 80) return null
                // Charging slows down near full, so the stretch to 80% goes faster than its share of the
                // time to 100%; 0.8 of the proportional time is close to how phones taper.
                val share = (80 - c.levelPct).toDouble() / (100 - c.levelPct)
                labels.chargeTo80(labels.duration(minutes((full * share * 0.8).toLong())))
            }
        }
    }

    private fun minutes(ms: Long) = ceil(ms / 60_000.0).toInt().coerceAtLeast(1)

    /** High UV now (until when it drops), or starting within the next 2 hours. */
    fun uvText(w: WeatherSnapshot, nowMs: Long, labels: Labels, clock: (Long) -> String): String? {
        val now = w.uvNow
        if (now != null && now >= UV_HIGH) {
            val ends = w.hourly.firstOrNull { (it.uvIndex ?: 0.0) < UV_HIGH }?.atEpochMs
            val peak = (listOf(now) + w.hourly.takeWhile { (it.uvIndex ?: 0.0) >= UV_HIGH }.mapNotNull { it.uvIndex }).max()
            return if (ends != null) labels.uvHighUntil(peak.roundToInt(), clock(ends)) else null
        }
        val starts = w.hourly.firstOrNull { it.atEpochMs - nowMs <= 2 * 3_600_000L && (it.uvIndex ?: 0.0) >= UV_HIGH }
            ?: return null
        val peak = w.hourly.mapNotNull { it.uvIndex }.filter { it >= UV_HIGH }.max()
        return labels.uvHighFrom(peak.roundToInt(), clock(starts.atEpochMs))
    }

    /** Tonight's low if it's frost territory (at or below [FROST_C] during the coming night hours). */
    fun frostLow(w: WeatherSnapshot): Double? =
        w.hourly.filter { !it.isDay }.minOfOrNull { it.tempC }?.takeIf { it <= FROST_C }

    /** The hour before sunset, or the hour after sunrise. */
    fun goldenText(sun: List<SunEvent>, nowMs: Long, labels: Labels, clock: (Long) -> String): String? {
        sun.firstOrNull { !it.sunrise && it.atEpochMs - nowMs in 0..GOLDEN_HOUR_MS }?.let {
            return labels.goldenEvening(clock(it.atEpochMs))
        }
        sun.firstOrNull { it.sunrise && nowMs - it.atEpochMs in 0 until GOLDEN_HOUR_MS }?.let {
            return labels.goldenMorning(clock(it.atEpochMs + GOLDEN_HOUR_MS))
        }
        return null
    }

    /** The top items that fit in [maxChars] when joined with " • " (always at least the first). */
    fun pick(items: List<Item>, maxChars: Int, max: Int = 2): List<Item> {
        val out = mutableListOf<Item>()
        var used = 0
        for (item in items) {
            if (out.size >= max) break
            val add = item.text.length + if (out.isEmpty()) 0 else 3
            if (out.isNotEmpty() && used + add > maxChars) break
            out += item
            used += add
        }
        return out
    }
}
