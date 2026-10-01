package com.dylan.glasswidget.data

/** What the rain is about to do, for the top line. */
sealed class RainOutlook {
    /** Dry now, wet from [atMs]. */
    data class Starting(val atMs: Long) : RainOutlook()
    /** Wet now, dry from [atMs]. */
    data class Easing(val atMs: Long) : RainOutlook()
    /** Wet now and for as far as the forecast looks. */
    object Continuing : RainOutlook()

    companion object {
        const val WET_MM_PER_HOUR = 0.3       // below this it's barely a drizzle
        const val LOOKAHEAD_MS = 2 * 3_600_000L

        /** From short rain slots (15-minute, or hourly), looking [LOOKAHEAD_MS] ahead. Null = nothing to say. */
        fun from(slots: List<RainSlot>, nowMs: Long): RainOutlook? {
            val ahead = slots.filter { it.atEpochMs + it.lengthMs > nowMs && it.atEpochMs < nowMs + LOOKAHEAD_MS }
                .sortedBy { it.atEpochMs }
            if (ahead.isEmpty()) return null
            fun wet(s: RainSlot) = s.mm / (s.lengthMs / 3_600_000.0) >= WET_MM_PER_HOUR
            return if (wet(ahead.first())) {
                ahead.firstOrNull { !wet(it) }?.let { Easing(maxOf(it.atEpochMs, nowMs)) } ?: Continuing
            } else {
                ahead.firstOrNull(::wet)?.let { Starting(it.atEpochMs) }
            }
        }
    }
}
