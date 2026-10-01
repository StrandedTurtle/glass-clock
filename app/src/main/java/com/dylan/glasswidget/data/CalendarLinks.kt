package com.dylan.glasswidget.data

import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Calendar links (.ics feeds), e.g. Proton Calendar's "Share with anyone" link. Proton doesn't put its
 * events into Android's calendar, so this is how the widget can show them.
 */
object CalendarLinks {
    private const val DAY_MS = 86_400_000L

    /** What a link holds: its own calendar name (if it has one) and events from a day ago to a week ahead. */
    data class Contents(val name: String?, val events: List<CalendarEvent>)

    /** Downloads and reads [url]. Throws on network or format failure, with a readable message. */
    suspend fun load(url: String, nowMs: Long = System.currentTimeMillis()): Contents =
        withContext(Dispatchers.IO) {
            val clean = Ics.normaliseUrl(url)
            require(clean.startsWith("https://", true) || clean.startsWith("http://", true)) {
                "That doesn't look like a link"
            }
            val body = httpGet(clean)
            require(body.contains("BEGIN:VCALENDAR", ignoreCase = true)) { "That link didn't return a calendar" }
            Contents(
                Ics.calendarName(body),
                Ics.expand(Ics.parse(body, ZoneId.systemDefault()), nowMs - DAY_MS, nowMs + 7 * DAY_MS, Ics.sourceKey(url)),
            )
        }

    suspend fun fetch(url: String, nowMs: Long = System.currentTimeMillis()): List<CalendarEvent> = load(url, nowMs).events
}
