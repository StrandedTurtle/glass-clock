package com.dylan.glasswidget.data

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class WidgetTextTest {
    private val zone = ZoneId.of("Europe/London") // UTC+1 in October
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()
    private val now = at("2026-10-01T09:00:00Z") // 10:00 local

    private fun timed(id: Long, begin: String, end: String) = CalendarEvent(id, "E$id", at(begin), at(end), false)
    private fun allDay(id: Long, date: String, days: Long = 1) = CalendarEvent(
        id, "A$id", at("${date}T00:00:00Z"),
        at("${date}T00:00:00Z") + days * 86_400_000L, true,
    )

    @Before fun locale() { Locale.setDefault(Locale.UK) }

    @Test fun clockFormats() {
        assertEquals("6:59", WidgetText.clock(at("2026-10-01T05:59:00Z"), zone, use24h = true))
        assertEquals("18:43", WidgetText.clock(at("2026-10-01T17:43:00Z"), zone, use24h = true))
        assertEquals("6:43", WidgetText.clock(at("2026-10-01T17:43:00Z"), zone, use24h = false))
    }

    @Test fun nextSunEventSkipsPast() {
        val sun = listOf(SunEvent(at("2026-10-01T05:59:00Z"), true), SunEvent(at("2026-10-01T17:43:00Z"), false))
        assertEquals(false, WidgetText.nextSunEvent(sun, now)!!.sunrise)
        assertNull(WidgetText.nextSunEvent(sun, at("2026-10-01T20:00:00Z")))
    }

    @Test fun ongoingBeatsUpcomingBeatsAllDay() {
        val ongoing = timed(1, "2026-10-01T08:30:00Z", "2026-10-01T09:30:00Z")
        val later = timed(2, "2026-10-01T13:00:00Z", "2026-10-01T14:00:00Z")
        val holiday = allDay(3, "2026-10-01")
        assertEquals(1L, WidgetText.pickEvent(listOf(later, holiday, ongoing), now, zone, true)!!.id)
        assertEquals(2L, WidgetText.pickEvent(listOf(later, holiday), now, zone, true)!!.id)
        assertEquals(3L, WidgetText.pickEvent(listOf(holiday), now, zone, true)!!.id)
        assertNull(WidgetText.pickEvent(listOf(holiday), now, zone, includeAllDay = false))
    }

    @Test fun finishedEventsAndPastAllDayAreIgnored() {
        val done = timed(1, "2026-10-01T07:00:00Z", "2026-10-01T08:00:00Z")
        val yesterday = allDay(2, "2026-09-30")
        assertNull(WidgetText.pickEvent(listOf(done, yesterday), now, zone, true))
    }

    @Test fun eventWhenLabels() {
        fun w(e: CalendarEvent) = WidgetText.eventWhen(e, now, zone, true, "Now", "Today", "Tomorrow")
        assertEquals("Now", w(timed(1, "2026-10-01T08:30:00Z", "2026-10-01T09:30:00Z")))
        assertEquals("14:30", w(timed(2, "2026-10-01T13:30:00Z", "2026-10-01T14:00:00Z")))
        assertEquals("Tomorrow 9:00", w(timed(3, "2026-10-02T08:00:00Z", "2026-10-02T09:00:00Z")))
        assertEquals("Today", w(allDay(4, "2026-10-01")))
        assertEquals("Tomorrow", w(allDay(5, "2026-10-02")))
        assertEquals("Today", w(allDay(6, "2026-09-30", days = 3))) // multi-day, started earlier
    }

    @Test fun boundaryIsSoonestChange() {
        val e = timed(1, "2026-10-01T13:30:00Z", "2026-10-01T14:00:00Z")
        val sun = listOf(SunEvent(at("2026-10-01T17:43:00Z"), false))
        assertEquals(e.beginEpochMs, WidgetText.nextBoundary(listOf(e), sun, now, zone))
        assertEquals(sun[0].atEpochMs, WidgetText.nextBoundary(emptyList(), sun, now, zone))
        // nothing else: local midnight (23:00Z at UTC+1)
        assertEquals(at("2026-10-01T23:00:00Z"), WidgetText.nextBoundary(emptyList(), emptyList(), now, zone))
    }
}
