package com.dylan.glasswidget.data

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsTest {
    private val london = ZoneId.of("Europe/London")
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun cal(vararg events: String) =
        "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Proton AG//Calendar//EN\r\n" +
            events.joinToString("") { "BEGIN:VEVENT\r\n$it\r\nEND:VEVENT\r\n" } + "END:VCALENDAR\r\n"

    private fun expand(ics: String, from: String, to: String) =
        Ics.expand(Ics.parse(ics, london), at(from), at(to), "link:x")

    @Test fun singleTimedEventWithTzid() {
        val ics = cal("UID:a\r\nSUMMARY:Dentist\r\nDTSTART;TZID=Europe/London:20261001T113000\r\nDTEND;TZID=Europe/London:20261001T121500")
        val e = expand(ics, "2026-10-01T00:00:00Z", "2026-10-03T00:00:00Z").single()
        assertEquals("Dentist", e.title)
        assertEquals(at("2026-10-01T10:30:00Z"), e.beginEpochMs) // 11:30 BST
        assertEquals(at("2026-10-01T11:15:00Z"), e.endEpochMs)
        assertEquals(false, e.allDay)
        assertEquals("link:x", e.source)
        assertTrue(e.fromLink)
    }

    @Test fun utcFloatingAndDurationForms() {
        val ics = cal(
            "UID:u\r\nSUMMARY:Call\r\nDTSTART:20261001T090000Z\r\nDURATION:PT45M",
            "UID:f\r\nSUMMARY:Gym\r\nDTSTART:20261001T180000\r\nDTEND:20261001T190000",
        )
        val (call, gym) = expand(ics, "2026-10-01T00:00:00Z", "2026-10-02T00:00:00Z")
        assertEquals(at("2026-10-01T09:45:00Z"), call.endEpochMs)
        assertEquals(at("2026-10-01T17:00:00Z"), gym.beginEpochMs) // floating -> device zone (BST)
    }

    @Test fun allDayEventsAreUtcMidnights() {
        val ics = cal("UID:h\r\nSUMMARY:Holiday\r\nDTSTART;VALUE=DATE:20261002\r\nDTEND;VALUE=DATE:20261004")
        val e = expand(ics, "2026-10-01T00:00:00Z", "2026-10-05T00:00:00Z").single()
        assertEquals(true, e.allDay)
        assertEquals(at("2026-10-02T00:00:00Z"), e.beginEpochMs)
        assertEquals(at("2026-10-04T00:00:00Z"), e.endEpochMs)
    }

    @Test fun foldedLinesAndEscapes() {
        val ics = cal("UID:e\r\nSUMMARY:Lunch\\, then\r\n  a walk\r\nDTSTART:20261001T120000Z\r\nDTEND:20261001T130000Z")
        assertEquals("Lunch, then a walk", expand(ics, "2026-10-01T00:00:00Z", "2026-10-02T00:00:00Z").single().title)
    }

    @Test fun weeklyWithExdateAndOverride() {
        val ics = cal(
            "UID:w\r\nSUMMARY:Standup\r\nDTSTART;TZID=Europe/London:20260907T093000\r\nDTEND;TZID=Europe/London:20260907T094500\r\n" +
                "RRULE:FREQ=WEEKLY;BYDAY=MO,WE\r\nEXDATE;TZID=Europe/London:20260930T093000",
            // the Monday 5 Oct occurrence moved to 10:00
            "UID:w\r\nSUMMARY:Standup (moved)\r\nRECURRENCE-ID;TZID=Europe/London:20261005T093000\r\n" +
                "DTSTART;TZID=Europe/London:20261005T100000\r\nDTEND;TZID=Europe/London:20261005T101500",
        )
        val got = expand(ics, "2026-09-28T00:00:00Z", "2026-10-08T00:00:00Z")
        assertEquals(
            listOf("2026-09-28T08:30:00Z", "2026-10-05T09:00:00Z", "2026-10-07T08:30:00Z").map(::at),
            got.map { it.beginEpochMs },
        ) // Wed 30 Sep excluded, Mon 5 Oct replaced by the moved one
        assertEquals("Standup (moved)", got[1].title)
    }

    @Test fun dailyIntervalCountAndUntil() {
        val counted = cal("UID:c\r\nSUMMARY:Pill\r\nDTSTART:20261001T080000Z\r\nDTEND:20261001T081000Z\r\nRRULE:FREQ=DAILY;INTERVAL=2;COUNT=3")
        assertEquals(
            listOf("2026-10-01T08:00:00Z", "2026-10-03T08:00:00Z", "2026-10-05T08:00:00Z").map(::at),
            expand(counted, "2026-09-01T00:00:00Z", "2026-11-01T00:00:00Z").map { it.beginEpochMs },
        )
        val until = cal("UID:d\r\nSUMMARY:X\r\nDTSTART:20261001T080000Z\r\nDTEND:20261001T081000Z\r\nRRULE:FREQ=DAILY;UNTIL=20261002T235959Z")
        assertEquals(2, expand(until, "2026-09-01T00:00:00Z", "2026-11-01T00:00:00Z").size)
    }

    @Test fun monthlyNthWeekdayAndYearly() {
        val monthly = cal("UID:m\r\nSUMMARY:Bins\r\nDTSTART:20260904T070000Z\r\nDTEND:20260904T071500Z\r\nRRULE:FREQ=MONTHLY;BYDAY=1FR")
        assertEquals(
            listOf("2026-09-04T07:00:00Z", "2026-10-02T07:00:00Z", "2026-11-06T07:00:00Z").map(::at),
            expand(monthly, "2026-09-01T00:00:00Z", "2026-11-30T00:00:00Z").map { it.beginEpochMs },
        )
        val lastDay = cal("UID:l\r\nSUMMARY:Rent\r\nDTSTART;VALUE=DATE:20260930\r\nRRULE:FREQ=MONTHLY;BYMONTHDAY=-1")
        assertEquals(
            listOf("2026-09-30T00:00:00Z", "2026-10-31T00:00:00Z").map(::at),
            expand(lastDay, "2026-09-15T00:00:00Z", "2026-11-15T00:00:00Z").map { it.beginEpochMs },
        )
        val birthday = cal("UID:b\r\nSUMMARY:Birthday\r\nDTSTART;VALUE=DATE:20001001\r\nRRULE:FREQ=YEARLY")
        assertEquals(at("2026-10-01T00:00:00Z"), expand(birthday, "2026-09-30T00:00:00Z", "2026-10-03T00:00:00Z").single().beginEpochMs)
    }

    @Test fun cancelledAndAlarmsAndJunkAreIgnored() {
        val ics = cal(
            "UID:x\r\nSUMMARY:Off\r\nSTATUS:CANCELLED\r\nDTSTART:20261001T080000Z\r\nDTEND:20261001T090000Z",
            "UID:y\r\nSUMMARY:With alarm\r\nDTSTART:20261001T100000Z\r\nDTEND:20261001T110000Z\r\n" +
                "BEGIN:VALARM\r\nTRIGGER:-PT15M\r\nSUMMARY:Alarm text\r\nEND:VALARM",
            "UID:z\r\nSUMMARY:No start",
        )
        val got = expand(ics, "2026-10-01T00:00:00Z", "2026-10-02T00:00:00Z")
        assertEquals(listOf("With alarm"), got.map { it.title })
    }

    @Test fun calendarNameAndShortLabel() {
        assertEquals("My calendar", Ics.calendarName("BEGIN:VCALENDAR\r\nX-WR-CALNAME:My calendar\r\nEND:VCALENDAR"))
        assertEquals(null, Ics.calendarName(cal()))
        assertEquals("calendar.proton.me", Ics.shortLabel("https://calendar.proton.me/api/calendar/v1/url/abc/calendar.ics?x=1"))
    }

    @Test fun urlHelpers() {
        assertEquals("https://calendar.proton.me/a.ics", Ics.normaliseUrl("  webcal://calendar.proton.me/a.ics "))
        assertEquals(Ics.sourceKey("webcal://h/a.ics"), Ics.sourceKey("https://h/a.ics"))
    }

    @Test fun calendarLinkListRoundTripsAndReadsOldFormat() {
        val links = listOf(CalendarLink("Personal", "https://calendar.proton.me/a.ics"), CalendarLink("Work", "webcal://x.org/b.ics"))
        assertEquals(links, CalendarLink.decode(CalendarLink.encode(links)))
        // the previous version stored bare URLs, one per line
        assertEquals(listOf(CalendarLink("calendar.proton.me", "https://calendar.proton.me/a.ics")),
            CalendarLink.decode("https://calendar.proton.me/a.ics\n\n"))
        // the same feed twice (webcal vs https) is kept once
        assertEquals(1, CalendarLink.decode("A\thttps://x.org/b.ics\nB\twebcal://x.org/b.ics").size)
    }
}
