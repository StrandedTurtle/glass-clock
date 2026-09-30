package com.dylan.glasswidget.data

import kotlinx.serialization.json.Json

/** JSON (de)serialisation and pruning for the cache blob; kept free of Android so it is unit-testable. */
object WeatherStoreCodec {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    const val MAX_ENTRIES = 8

    fun encode(store: WeatherStore): String = json.encodeToString(WeatherStore.serializer(), store)

    /** Never throws: corrupt or missing data just means "nothing cached yet". */
    fun decode(text: String?): WeatherStore =
        if (text.isNullOrBlank()) WeatherStore()
        else runCatching { json.decodeFromString(WeatherStore.serializer(), text) }.getOrDefault(WeatherStore())

    /** Adds [fresh] over [old], keeping only the [MAX_ENTRIES] most recently fetched. */
    fun merge(old: WeatherStore, fresh: Map<String, WeatherSnapshot>): WeatherStore {
        val merged = old.entries + fresh
        val kept = merged.entries.sortedByDescending { it.value.fetchedAtEpochMs }.take(MAX_ENTRIES)
        return WeatherStore(kept.associate { it.key to it.value })
    }
}
