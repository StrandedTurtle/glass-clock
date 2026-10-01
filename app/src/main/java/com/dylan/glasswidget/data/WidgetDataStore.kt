package com.dylan.glasswidget.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.widgetDataStore by preferencesDataStore(name = "weather_cache")

/** Everything the widget renders that isn't a setting. */
data class WidgetData(
    val weather: WeatherStore = WeatherStore(),
    val events: List<CalendarEvent> = emptyList(),
    /** When calendar links were last downloaded (0 = never). */
    val linksFetchedAtMs: Long = 0,
)

/**
 * One shared cache for every placed widget: weather keyed by location (widgets on the same city
 * share a fetch) plus upcoming calendar events. Workers write it; the widget only ever reads it.
 */
object WidgetDataStore {
    private val KEY_WEATHER = stringPreferencesKey("weather_store_json")
    private val KEY_EVENTS = stringPreferencesKey("events_json")
    private val KEY_LINKS_FETCHED = longPreferencesKey("links_fetched_at")

    fun flow(context: Context): Flow<WidgetData> =
        context.applicationContext.widgetDataStore.data.map {
            WidgetData(
                WeatherStoreCodec.decode(it[KEY_WEATHER]),
                WeatherStoreCodec.decodeEvents(it[KEY_EVENTS]),
                it[KEY_LINKS_FETCHED] ?: 0,
            )
        }

    suspend fun load(context: Context): WidgetData = flow(context).first()

    suspend fun saveWeather(context: Context, fresh: Map<String, WeatherSnapshot>) {
        if (fresh.isEmpty()) return
        context.applicationContext.widgetDataStore.edit { prefs ->
            val merged = WeatherStoreCodec.merge(WeatherStoreCodec.decode(prefs[KEY_WEATHER]), fresh)
            prefs[KEY_WEATHER] = WeatherStoreCodec.encode(merged)
        }
    }

    suspend fun saveEvents(context: Context, events: List<CalendarEvent>, linksFetchedAtMs: Long? = null) {
        context.applicationContext.widgetDataStore.edit {
            it[KEY_EVENTS] = WeatherStoreCodec.encodeEvents(events)
            if (linksFetchedAtMs != null) it[KEY_LINKS_FETCHED] = linksFetchedAtMs
        }
    }
}
