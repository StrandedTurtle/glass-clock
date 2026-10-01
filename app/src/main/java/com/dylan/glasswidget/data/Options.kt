package com.dylan.glasswidget.data

/** Persisted option values. Each enum round-trips through a stable string [key] and falls back to a default. */

/** Glass strength: Soft is denser frost, Clear lets more wallpaper through. */
enum class GlassVariant(val key: String) {
    Clear("clear"), Soft("soft");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: Soft
    }
}

/** Colour of the glass and text. Ink is light glass with dark text, for bright wallpapers. */
enum class TintMode(val key: String) {
    Frost("frost"), Smoke("smoke"), Ink("ink"), Dynamic("dynamic");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: Frost
    }
}

/** Glass is drawn from images (kept on time by the minute ticker) in a chosen [ClockFace]; Solid is a TextClock. */
enum class ClockStyle(val key: String) {
    Glass("glass"), Solid("solid");

    val usesImages: Boolean get() = this == Glass

    companion object {
        // "crystal" was the name of today's glass look while it was an alternative to the original.
        fun from(key: String?) = if (key == "crystal") Glass else entries.firstOrNull { it.key == key } ?: Glass
    }
}

enum class WidgetAlignment(val key: String) {
    Center("center"), Start("start");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: Center
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

/** Optional weather bits. [inPill] ones sit next to the temperature; the rest go in the details pill. */
enum class WeatherDetail(val key: String, val inPill: Boolean, val onByDefault: Boolean) {
    Condition("condition", true, true),
    HighLow("high_low", true, true),
    SunTimes("sun", false, true),
    FeelsLike("feels", false, true),
    RainChance("rain", false, true),
    Wind("wind", false, true),
    Humidity("humidity", false, false),
    Uv("uv", false, false),
    AirQuality("aqi", false, true),
    Pollen("pollen", false, true),
    Moon("moon", false, true),
    Tomorrow("tomorrow", false, true),
    /** Not an item: a strip of the next few hours inside the card. */
    Hourly("hourly", false, true);

    companion object {
        val defaults: Set<WeatherDetail> = entries.filter { it.onByDefault }.toSet()

        /** null (never set) = defaults; otherwise exactly what was saved, unknown keys ignored. */
        fun fromKeys(keys: Set<String>?): Set<WeatherDetail> =
            keys?.let { saved -> entries.filter { it.key in saved }.toSet() } ?: defaults
    }
}

/** The four responsive breakpoints. */
enum class WidgetSize {
    Compact, Standard, Tall, Large;

    companion object {
        // Breakpoints are 70 / 110 / 180 / 250 dp tall; thresholds sit between them.
        fun fromHeightDp(heightDp: Float): WidgetSize = when {
            heightDp >= 215f -> Large
            heightDp >= 145f -> Tall
            heightDp >= 90f -> Standard
            else -> Compact
        }
    }
}

object Limits {
    const val TEXT_SCALE_MIN = 0.8f
    const val TEXT_SCALE_MAX = 1.3f
    // 100% = as big as the widget allows once the pills are placed; smaller leaves breathing room.
    const val CLOCK_SCALE_MIN = 0.5f
    const val CLOCK_SCALE_MAX = 1.0f

    fun clampTextScale(v: Float) = v.coerceIn(TEXT_SCALE_MIN, TEXT_SCALE_MAX)
    fun clampClockScale(v: Float) = v.coerceIn(CLOCK_SCALE_MIN, CLOCK_SCALE_MAX)
}
