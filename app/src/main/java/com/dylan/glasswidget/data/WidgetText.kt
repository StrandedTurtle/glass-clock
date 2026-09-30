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
