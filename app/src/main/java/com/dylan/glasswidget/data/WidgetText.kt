package com.dylan.glasswidget.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Time-dependent choices and labels. Pure (clock and zone passed in) so they are unit-testable. */
object WidgetText {

    /** "6:59" (24h shows "18:45"; 12h "6:45"). */
    fun clock(epochMs: Long, zone: ZoneId, use24h: Boolean): String =
        DateTimeFormatter.ofPattern(if (use24h) "H:mm" else "h:mm", Locale.getDefault())
            .format(Instant.ofEpochMilli(epochMs).atZone(zone))

    /** What the glass clock shows: "0745", "07:45", "745" or "7:45" (HyperOS's lockscreen has no colon). */
    fun clockText(epochMs: Long, zone: ZoneId, use24h: Boolean, colon: Boolean): String {
        val sep = if (colon) ":" else ""
        val pattern = if (use24h) "HH${sep}mm" else "h${sep}mm"
        return DateTimeFormatter.ofPattern(pattern, Locale.ROOT).format(Instant.ofEpochMilli(epochMs).atZone(zone))
    }

    // Width/height of the glass digit drawables (printed by tools/build_clock_digits.py).
    const val DIGIT_ASPECT = 0.4014f
    const val COLON_ASPECT = 0.1839f

    /** Width of [text] in glass digits at a given height. */
    fun clockWidth(text: String, heightDp: Float): Float =
        text.sumOf { (if (it == ':') COLON_ASPECT else DIGIT_ASPECT).toDouble() }.toFloat() * heightDp

    /**
     * Greedily packs items (by estimated width) into rows no wider than [maxWidth], at most [perRow]
     * per row (Glance caps a row at 10 children; each item plus its gap takes two). Returns indices.
     */
    fun packRows(widths: List<Float>, maxWidth: Float, gap: Float, perRow: Int = 5): List<List<Int>> {
        val rows = mutableListOf<MutableList<Int>>()
        var used = 0f
        widths.forEachIndexed { i, w ->
            val row = rows.lastOrNull()
            if (row != null && row.size < perRow && used + gap + w <= maxWidth) {
                row += i
                used += gap + w
            } else {
                rows += mutableListOf(i)
                used = w
            }
        }
        return rows
    }

    /** The next sunrise or sunset after [nowMs], if the forecast covers it. */
    fun nextSunEvent(events: List<SunEvent>, nowMs: Long): SunEvent? =
        events.filter { it.atEpochMs > nowMs }.minByOrNull { it.atEpochMs }

    /**
     * The event to show: one happening now, else the soonest upcoming timed event, else an all-day
     * event covering today, else the soonest upcoming all-day one. Declined/hidden ones never get here.
     */
    fun pickEvent(events: List<CalendarEvent>, nowMs: Long, zone: ZoneId, includeAllDay: Boolean): CalendarEvent? {
        val timed = events.filter { !it.allDay && it.endEpochMs > nowMs }
        timed.filter { it.beginEpochMs <= nowMs }.minByOrNull { it.endEpochMs }?.let { return it }
        timed.minByOrNull { it.beginEpochMs }?.let { return it }
        if (!includeAllDay) return null
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(nowMs), zone)
        val allDay = events.filter { it.allDay && allDayEnd(it) > today }
        return allDay.firstOrNull { allDayStart(it) <= today }
            ?: allDay.minByOrNull { allDayStart(it) }
    }

    /** "Now", "14:30", "Tomorrow 9:00", "Today", "Tomorrow", "Sat 4 Oct 9:00"… */
    fun eventWhen(e: CalendarEvent, nowMs: Long, zone: ZoneId, use24h: Boolean, now: String, today: String, tomorrow: String): String {
        val todayDate = LocalDate.ofInstant(Instant.ofEpochMilli(nowMs), zone)
        if (e.allDay) {
            val start = allDayStart(e)
            return when {
                start <= todayDate -> today
                start == todayDate.plusDays(1) -> tomorrow
                else -> DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).format(start)
            }
        }
        if (e.beginEpochMs <= nowMs) return now
        val startDate = LocalDate.ofInstant(Instant.ofEpochMilli(e.beginEpochMs), zone)
        val time = clock(e.beginEpochMs, zone, use24h)
        return when (startDate) {
            todayDate -> time
            todayDate.plusDays(1) -> "$tomorrow $time"
            else -> DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).format(startDate) + " " + time
        }
    }

    /**
     * When the widget's content next goes stale without any new data: an event starting or ending,
     * sunrise/sunset flipping the top line, or midnight turning "Tomorrow" into today. Null if none.
     */
    fun nextBoundary(events: List<CalendarEvent>, sun: List<SunEvent>, nowMs: Long, zone: ZoneId): Long? {
        val midnight = LocalDate.ofInstant(Instant.ofEpochMilli(nowMs), zone).plusDays(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val candidates = buildList {
            events.filter { !it.allDay }.forEach { add(it.beginEpochMs); add(it.endEpochMs) }
            sun.forEach { add(it.atEpochMs) }
            add(midnight)
        }
        return candidates.filter { it > nowMs }.minOrNull()
    }

    // All-day instances are stored as UTC midnights; their dates are the calendar dates.
    private fun allDayStart(e: CalendarEvent): LocalDate = LocalDate.ofInstant(Instant.ofEpochMilli(e.beginEpochMs), ZoneOffset.UTC)
    private fun allDayEnd(e: CalendarEvent): LocalDate = LocalDate.ofInstant(Instant.ofEpochMilli(e.endEpochMs), ZoneOffset.UTC)
}
