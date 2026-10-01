package com.dylan.glasswidget.data

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Minimal iCalendar (.ics) reader for calendar *links*, e.g. Proton Calendar's "Share with anyone" link
 * (Proton keeps its events out of Android's calendar, so a link is the only way to read them).
 *
 * Handles what real feeds use: all-day / UTC / TZID / floating times, DTEND or DURATION, RRULE
 * (DAILY, WEEKLY, MONTHLY, YEARLY with INTERVAL, COUNT, UNTIL, BYDAY incl. "2MO"/"-1FR", BYMONTHDAY,
 * BYMONTH), EXDATE, RECURRENCE-ID overrides and cancelled events. Pure Kotlin, so it's unit-tested.
 */
object Ics {

    data class Event(
        val uid: String,
        val summary: String,
        val start: ZonedDateTime,
        val end: ZonedDateTime,
        val allDay: Boolean,
        val rrule: Map<String, String>?,
        val exdates: Set<Long>,
        val recurrenceId: Long?,
        val cancelled: Boolean,
    )

    /** Parses every VEVENT; floating times are read in [zone]. Malformed events are skipped. */
    fun parse(text: String, zone: ZoneId): List<Event> {
        val events = mutableListOf<Event>()
        var props: MutableList<Prop>? = null
        var depth = 0 // nested components inside a VEVENT (VALARM) are ignored
        for (line in unfold(text)) {
            when {
                line.equals("BEGIN:VEVENT", true) -> { props = mutableListOf(); depth = 0 }
                props != null && line.startsWith("BEGIN:", true) -> depth++
                props != null && line.equals("END:VEVENT", true) -> {
                    runCatching { toEvent(props, zone) }.getOrNull()?.let(events::add)
                    props = null
                }
                props != null && line.startsWith("END:", true) -> depth--
                props != null && depth == 0 -> parseProp(line)?.let(props::add)
            }
        }
        return events
    }

    /**
     * Every occurrence overlapping [fromMs, toMs), as widget events tagged with [source].
     * All-day events come out as UTC midnights, like Android's calendar stores them.
     */
    fun expand(events: List<Event>, fromMs: Long, toMs: Long, source: String): List<CalendarEvent> {
        val overrides = events.filter { it.recurrenceId != null }.groupBy { it.uid }
        val out = mutableListOf<CalendarEvent>()
        for (e in events) {
            if (e.recurrenceId != null) {
                if (!e.cancelled) add(out, e, e.start, fromMs, toMs, source)
                continue
            }
            if (e.cancelled) continue
            val replaced = overrides[e.uid].orEmpty().mapNotNull { it.recurrenceId }.toSet()
            occurrences(e, toMs).forEach { occ ->
                val key = occ.toInstant().toEpochMilli()
                if (key !in e.exdates && key !in replaced) add(out, e, occ, fromMs, toMs, source)
            }
        }
        return out.sortedBy { it.beginEpochMs }
    }

    private fun add(out: MutableList<CalendarEvent>, e: Event, start: ZonedDateTime, fromMs: Long, toMs: Long, source: String) {
        val length = Duration.between(e.start, e.end)
        val (begin, end) = if (e.allDay) {
            // Keep all-day events on their calendar date: UTC midnights, like Android stores them.
            val day = start.toLocalDate()
            val days = maxOf(1L, length.toDays())
            day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() to
                day.plusDays(days).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } else {
            start.toInstant().toEpochMilli() to start.plus(length).toInstant().toEpochMilli()
        }
        // All-day events are matched against today's local date later, so keep them a day either side.
        val slack = if (e.allDay) 86_400_000L else 0L
        if (end + slack <= fromMs || begin - slack >= toMs) return
        out += CalendarEvent(
            id = (e.uid + begin).hashCode().toLong(),
            title = e.summary,
            beginEpochMs = begin,
            endEpochMs = end,
            allDay = e.allDay,
            source = source,
        )
    }

    // ---- recurrence ----

    private const val MAX_PERIODS = 3000

    /** Occurrence starts in time order, from DTSTART until [toMs] (or COUNT / UNTIL). */
    private fun occurrences(e: Event, toMs: Long): Sequence<ZonedDateTime> = sequence {
        val r = e.rrule
        val freq = r?.get("FREQ")?.uppercase()
        if (r == null || freq !in setOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY")) {
            yield(e.start) // not recurring, or a rule we don't understand: the first occurrence only
            return@sequence
        }
        val interval = r["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val count = r["COUNT"]?.toIntOrNull()
        val until = r["UNTIL"]?.let { parseUntil(it, e.start.zone) }
        val byDay = r["BYDAY"]?.split(',')?.mapNotNull(::parseByDay).orEmpty()
        val byMonthDay = r["BYMONTHDAY"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
        val byMonth = r["BYMONTH"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
        val time = e.start.toLocalTime()
        val zone = e.start.zone
        val first = e.start.toLocalDate()

        var emitted = 0
        for (k in 0 until MAX_PERIODS) {
            val dates: List<LocalDate> = when (freq) {
                "DAILY" -> listOf(first.plusDays(k.toLong() * interval))
                "WEEKLY" -> {
                    val weekStart = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .plusWeeks(k.toLong() * interval)
                    val days = byDay.map { it.second }.ifEmpty { listOf(first.dayOfWeek) }
                    days.distinct().map { weekStart.with(TemporalAdjusters.nextOrSame(it)) }
                }
                "MONTHLY" -> monthDates(YearMonth.from(first).plusMonths(k.toLong() * interval), first, byDay, byMonthDay)
                "YEARLY" -> {
                    val year = first.year + k * interval
                    val months = byMonth.ifEmpty { listOf(first.monthValue) }
                    months.flatMap { m -> monthDates(YearMonth.of(year, m), first, byDay, byMonthDay) }
                }
                else -> emptyList()
            }
            for (d in dates.sorted()) {
                if (d < first) continue
                val occ = ZonedDateTime.of(d, time, zone)
                if (until != null && occ.isAfter(until)) return@sequence
                if (occ.toInstant().toEpochMilli() >= toMs) return@sequence
                if (count != null && emitted >= count) return@sequence
                emitted++
                yield(occ)
            }
        }
    }

    /** Dates in [month] matching BYDAY / BYMONTHDAY, or DTSTART's day of month (if the month has it). */
    private fun monthDates(month: YearMonth, first: LocalDate, byDay: List<Pair<Int, DayOfWeek>>, byMonthDay: List<Int>): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        byMonthDay.forEach { n ->
            val day = if (n > 0) n else month.lengthOfMonth() + n + 1
            if (day in 1..month.lengthOfMonth()) out += month.atDay(day)
        }
        byDay.forEach { (n, dow) ->
            val all = generateSequence(month.atDay(1).with(TemporalAdjusters.nextOrSame(dow))) { it.plusWeeks(1) }
                .takeWhile { it.month == month.month }.toList()
            when {
                n == 0 -> out += all
                n > 0 -> all.getOrNull(n - 1)?.let(out::add)
                else -> all.getOrNull(all.size + n)?.let(out::add)
            }
        }
        if (byDay.isEmpty() && byMonthDay.isEmpty() && first.dayOfMonth <= month.lengthOfMonth()) {
            out += month.atDay(first.dayOfMonth)
        }
        return out
    }

    private fun parseByDay(s: String): Pair<Int, DayOfWeek>? {
        val t = s.trim().uppercase()
        if (t.length < 2) return null
        val dow = when (t.takeLast(2)) {
            "MO" -> DayOfWeek.MONDAY; "TU" -> DayOfWeek.TUESDAY; "WE" -> DayOfWeek.WEDNESDAY
            "TH" -> DayOfWeek.THURSDAY; "FR" -> DayOfWeek.FRIDAY; "SA" -> DayOfWeek.SATURDAY
            "SU" -> DayOfWeek.SUNDAY; else -> return null
        }
        val n = t.dropLast(2).ifEmpty { "0" }.toIntOrNull() ?: return null
        return n to dow
    }

    private fun parseUntil(v: String, zone: ZoneId): ZonedDateTime? = runCatching {
        if (v.length == 8) LocalDate.parse(v, DATE).atTime(LocalTime.MAX).atZone(zone)
        else if (v.endsWith("Z")) LocalDateTime.parse(v.dropLast(1), DATE_TIME).atZone(ZoneOffset.UTC)
        else LocalDateTime.parse(v, DATE_TIME).atZone(zone)
    }.getOrNull()

    // ---- properties ----

    private data class Prop(val name: String, val params: Map<String, String>, val value: String)

    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    /** Lines folded with CRLF + space/tab are joined back together. */
    private fun unfold(text: String): List<String> {
        val out = mutableListOf<String>()
        for (raw in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && out.isNotEmpty()) out[out.lastIndex] += raw.substring(1)
            else if (raw.isNotEmpty()) out += raw
        }
        return out
    }

    private fun parseProp(line: String): Prop? {
        var inQuotes = false
        var colon = -1
        for ((i, c) in line.withIndex()) {
            if (c == '"') inQuotes = !inQuotes
            if (c == ':' && !inQuotes) { colon = i; break }
        }
        if (colon <= 0) return null
        val head = line.substring(0, colon).split(';')
        val params = head.drop(1).mapNotNull {
            val eq = it.indexOf('=')
            if (eq > 0) it.substring(0, eq).uppercase() to it.substring(eq + 1).trim('"') else null
        }.toMap()
        return Prop(head[0].uppercase(), params, line.substring(colon + 1))
    }

    private fun toEvent(props: List<Prop>, zone: ZoneId): Event? {
        fun prop(name: String) = props.firstOrNull { it.name == name }
        val dtStart = prop("DTSTART") ?: return null
        val allDay = dtStart.params["VALUE"]?.equals("DATE", true) == true || dtStart.value.length == 8
        val start = time(dtStart, zone) ?: return null
        val end = prop("DTEND")?.let { time(it, zone) }
            ?: prop("DURATION")?.let { runCatching { start.plus(parseDuration(it.value)) }.getOrNull() }
            ?: if (allDay) start.plusDays(1) else start
        val rrule = prop("RRULE")?.value?.split(';')?.mapNotNull {
            val eq = it.indexOf('=')
            if (eq > 0) it.substring(0, eq).uppercase() to it.substring(eq + 1) else null
        }?.toMap()
        val exdates = props.filter { it.name == "EXDATE" }.flatMap { p ->
            p.value.split(',').mapNotNull { v -> time(p.copy(value = v), zone)?.toInstant()?.toEpochMilli() }
        }.toSet()
        return Event(
            uid = prop("UID")?.value ?: (prop("SUMMARY")?.value.orEmpty() + dtStart.value),
            summary = unescape(prop("SUMMARY")?.value.orEmpty()),
            start = start,
            end = end,
            allDay = allDay,
            rrule = rrule,
            exdates = exdates,
            recurrenceId = prop("RECURRENCE-ID")?.let { time(it, zone)?.toInstant()?.toEpochMilli() },
            cancelled = prop("STATUS")?.value?.equals("CANCELLED", true) == true,
        )
    }

    /** DATE, UTC (Z), TZID or floating date-time. All-day dates are kept in [zone] for recurrence. */
    private fun time(p: Prop, zone: ZoneId): ZonedDateTime? = runCatching {
        val v = p.value.trim()
        val tz = p.params["TZID"]?.let { id -> runCatching { ZoneId.of(id) }.getOrNull() } ?: zone
        when {
            v.length == 8 -> LocalDate.parse(v, DATE).atStartOfDay(zone)
            // UTC times stay in UTC, so their repeats keep the same UTC time across DST (RFC 5545).
            v.endsWith("Z") -> LocalDateTime.parse(v.dropLast(1), DATE_TIME).atZone(ZoneOffset.UTC)
            else -> LocalDateTime.parse(v, DATE_TIME).atZone(tz)
        }
    }.getOrNull()

    /** RFC 5545 duration, e.g. "PT1H30M", "P1D", "-PT15M". */
    private fun parseDuration(v: String): Duration {
        val negative = v.startsWith("-")
        val body = v.trimStart('-', '+')
        var d = Duration.ZERO
        Regex("(\\d+)([WDHMS])").findAll(body).forEach { m ->
            val n = m.groupValues[1].toLong()
            d = d.plus(
                when (m.groupValues[2]) {
                    "W" -> Duration.ofDays(7 * n); "D" -> Duration.ofDays(n); "H" -> Duration.ofHours(n)
                    "M" -> Duration.ofMinutes(n); else -> Duration.ofSeconds(n)
                }
            )
        }
        return if (negative) d.negated() else d
    }

    private fun unescape(s: String) =
        s.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\").trim()

    /** The feed's own calendar name (X-WR-CALNAME), e.g. "Personal", if it has one. */
    fun calendarName(text: String): String? =
        unfold(text).firstOrNull { it.startsWith("X-WR-CALNAME", true) }
            ?.let { parseProp(it)?.value }?.let(::unescape)?.takeIf { it.isNotBlank() }

    /** A short readable label for a link with no name: its host, e.g. "calendar.proton.me". */
    fun shortLabel(url: String): String =
        normaliseUrl(url).substringAfter("://").substringBefore('/').ifBlank { url.take(32) }

    /** "webcal://…" -> "https://…"; anything else unchanged. */
    fun normaliseUrl(url: String): String = url.trim().let {
        if (it.startsWith("webcal://", true)) "https://" + it.substring(9) else it
    }

    /** Stable cache tag for events from one link. */
    fun sourceKey(url: String): String = "link:" + normaliseUrl(url).hashCode().toUInt().toString(16)

}
