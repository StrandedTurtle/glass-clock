package com.dylan.glasswidget.data

import java.time.ZoneId
import kotlin.math.ceil

/**
 * The line above the clock: the most useful thing right now, like the lockscreen's top line. Candidates
 * are ranked; the widget shows the top one or two that fit. Pure (clock, zone and wording passed in).
 */
object SmartLine {

    enum class Kind { Warning, Event, Rain, Alarm }

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
    )

    data class Sources(val events: Boolean, val rain: Boolean, val alarm: Boolean, val warnings: Boolean)

    const val COUNTDOWN_MS = 60 * 60_000L
    const val ALARM_AHEAD_MS = 12 * 3_600_000L

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
    ): List<Item> = buildList {
        if (sources.warnings) warnings.maxByOrNull { it.level }?.let { w ->
            add(Item(Kind.Warning, labels.warning(w.level, w.hazard), if (w.level == WarningLevel.Yellow) 3 else 0))
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
            add(Item(Kind.Event, "$whenText · $title", if (now || soon) 1 else 5))
        }
        if (sources.rain && rain != null) {
            val text = when (rain) {
                is RainOutlook.Starting -> labels.rainFrom(WidgetText.clock(rain.atMs, zone, use24h))
                is RainOutlook.Easing -> labels.rainEasing(WidgetText.clock(rain.atMs, zone, use24h))
                RainOutlook.Continuing -> labels.rainContinuing
            }
            add(Item(Kind.Rain, text, 2))
        }
        if (sources.alarm && alarmMs != null && alarmMs - nowMs in 1..ALARM_AHEAD_MS) {
            add(Item(Kind.Alarm, labels.alarm(WidgetText.clock(alarmMs, zone, use24h)), 4))
        }
    }.sortedBy { it.priority }

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
