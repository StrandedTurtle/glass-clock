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

    @Test fun clockTextVariants() {
        val t = at("2026-10-01T06:45:00Z") // 07:45 local
        assertEquals("0745", WidgetText.clockText(t, zone, use24h = true, colon = false))
        assertEquals("07:45", WidgetText.clockText(t, zone, use24h = true, colon = true))
        assertEquals("745", WidgetText.clockText(t, zone, use24h = false, colon = false))
        val pm = at("2026-10-01T12:05:00Z") // 13:05 local
        assertEquals("1:05", WidgetText.clockText(pm, zone, use24h = false, colon = true))
        assertEquals("1305", WidgetText.clockText(pm, zone, use24h = true, colon = false))
    }

    @Test fun clockWidthUsesDigitAndColonAspects() {
        assertEquals(4 * WidgetText.DIGIT_ASPECT * 100f, WidgetText.clockWidth("0745", 100f), 0.01f)
        assertEquals((4 * WidgetText.DIGIT_ASPECT + WidgetText.COLON_ASPECT) * 100f, WidgetText.clockWidth("07:45", 100f), 0.01f)
    }

    @Test fun packRowsWrapsByWidthAndCount() {
        // 3 fit in 100 (30 + 5 + 30 + 5 + 30 = 100), the 4th wraps.
        assertEquals(listOf(listOf(0, 1, 2), listOf(3)), WidgetText.packRows(listOf(30f, 30f, 30f, 30f), 100f, 5f))
        // An item wider than the row still gets a row of its own.
        assertEquals(listOf(listOf(0), listOf(1)), WidgetText.packRows(listOf(150f, 10f), 100f, 5f).let { listOf(it[0], it[1]) })
        // Count cap.
        assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4)), WidgetText.packRows(List(5) { 1f }, 100f, 1f, perRow = 2))
        assertEquals(emptyList<List<Int>>(), WidgetText.packRows(emptyList(), 100f, 5f))
    }
}
