package com.dylan.glasswidget.data

import java.util.Locale

/** A place to fetch weather for. [key] identifies its cache slot. */
data class Location(val lat: Double, val lon: Double, val label: String? = null) {
    val key: String get() = LocationKey.of(lat, lon)
}

object LocationKey {
    /** Rounded to ~1 km so jitter in a device fix doesn't create a new cache entry every time. */
    fun of(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.2f,%.2f", lat, lon)
}
