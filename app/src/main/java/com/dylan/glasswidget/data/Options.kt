package com.dylan.glasswidget.data

/** Persisted option values. Each enum round-trips through a stable string [key] and falls back to a default. */

enum class GlassVariant(val key: String) {
    Clear("clear"), Soft("soft");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: Soft
    }
}

enum class PaddingMode(val key: String, val factor: Float) {
    Tight("tight", 0.6f), Normal("normal", 1f), Roomy("roomy", 1.4f);

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: Normal
    }
}

enum class TintMode(val key: String) {
    Dynamic("dynamic"), Light("light"), Dark("dark");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: Dynamic
    }
}

enum class TempUnit(val key: String) {
    C("c"), F("f");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: C
    }
}

enum class LocationMode(val key: String) {
    City("city"), Device("device");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: City
    }
}

/** "System" lets the TextClock follow the phone's 12/24h setting. */
enum class HourMode(val key: String) {
    System("system"), H12("12"), H24("24");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: System
    }
}

enum class DatePreset(val key: String, val pattern: String) {
    Short("short", "EEE d MMM"),
    Long("long", "EEEE d MMMM"),
    DayMonth("day_month", "d MMM"),
    American("us", "EEE, MMM d"),
    Numeric("numeric", "dd/MM/yyyy");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: Short
    }
}

/** The three responsive breakpoints (§9). */
enum class WidgetSize {
    Compact, Standard, Tall;

    companion object {
        // Breakpoints are 70 / 110 / 180 dp tall; thresholds sit between them.
        fun fromHeightDp(heightDp: Float): WidgetSize = when {
            heightDp >= 145f -> Tall
            heightDp >= 90f -> Standard
            else -> Compact
        }
    }
}

object Limits {
    const val TEXT_SCALE_MIN = 0.8f
    const val TEXT_SCALE_MAX = 1.3f
    const val RADIUS_MIN = 16
    const val RADIUS_MAX = 36
    const val RADIUS_STEP = 4
    const val DEFAULT_RADIUS = 28

    /** Radii come in fixed steps because each one has its own glass-edge drawable. */
    val RADII: List<Int> = (RADIUS_MIN..RADIUS_MAX step RADIUS_STEP).toList()

    fun clampTextScale(v: Float) = v.coerceIn(TEXT_SCALE_MIN, TEXT_SCALE_MAX)

    fun snapRadius(dp: Int): Int = RADII.minByOrNull { kotlin.math.abs(it - dp) } ?: DEFAULT_RADIUS
}
