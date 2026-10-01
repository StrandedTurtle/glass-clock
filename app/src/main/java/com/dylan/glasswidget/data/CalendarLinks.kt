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

    /** Downloads [url] and returns its events from a day ago to a week ahead. Throws on network/parse failure. */
    suspend fun fetch(url: String, nowMs: Long = System.currentTimeMillis()): List<CalendarEvent> =
        withContext(Dispatchers.IO) {
            val body = httpGet(Ics.normaliseUrl(url))
            require(body.contains("BEGIN:VCALENDAR", ignoreCase = true)) { "That link didn't return a calendar" }
            Ics.expand(Ics.parse(body, ZoneId.systemDefault()), nowMs - DAY_MS, nowMs + 7 * DAY_MS, Ics.sourceKey(url))
        }
}
