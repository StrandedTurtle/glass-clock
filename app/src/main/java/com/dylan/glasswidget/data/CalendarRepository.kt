package com.dylan.glasswidget.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

/** Upcoming events from every visible calendar on the phone (whatever accounts sync into it). */
object CalendarRepository {
    private const val DAY_MS = 86_400_000L

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Instances from a day ago (so an all-day event covering today is included) to [aheadMs] ahead,
     * skipping hidden calendars and events you've declined. Empty without permission.
     */
    fun upcoming(context: Context, nowMs: Long = System.currentTimeMillis(), aheadMs: Long = 7 * DAY_MS): List<CalendarEvent> {
        if (!hasPermission(context)) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, nowMs - DAY_MS)
            ContentUris.appendId(it, nowMs + aheadMs)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )
        val selection = "${CalendarContract.Instances.VISIBLE} = 1 AND " +
            "${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED}"
        return runCatching {
            context.contentResolver.query(uri, projection, selection, null, "${CalendarContract.Instances.BEGIN} ASC")
                ?.use { c ->
                    buildList {
                        while (c.moveToNext() && size < 40) {
                            val end = c.getLong(3)
                            val allDay = c.getInt(4) == 1
                            // All-day ends are UTC midnights; keep them a day longer and let the picker decide.
                            if (end <= nowMs && !(allDay && end > nowMs - DAY_MS)) continue
                            add(CalendarEvent(c.getLong(0), c.getString(1).orEmpty(), c.getLong(2), end, allDay))
                        }
                    }
                }.orEmpty()
        }.getOrDefault(emptyList())
    }

    /**
     * Names of the calendars Android shares with other apps ("Personal · you@gmail.com"), for showing
     * in settings which calendars the widget can actually see. Null without permission.
     */
    fun visibleCalendars(context: Context): List<String>? {
        if (!hasPermission(context)) return null
        val projection = arrayOf(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME)
        return runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, projection,
                "${CalendarContract.Calendars.VISIBLE} = 1", null, null,
            )?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val name = c.getString(0).orEmpty()
                        val account = c.getString(1).orEmpty()
                        add(if (account.isBlank() || account == name) name else "$name · $account")
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    /** Opens the event in the user's calendar app. */
    fun viewIntent(event: CalendarEvent): Intent =
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.beginEpochMs)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endEpochMs)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
