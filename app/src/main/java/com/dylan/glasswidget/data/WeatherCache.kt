package com.dylan.glasswidget.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.weatherDataStore by preferencesDataStore(name = "weather_cache")

/**
 * One shared cache for every placed widget, keyed by location so widgets showing the same
 * city share a single fetch. The widget only ever reads this; the worker is the only writer.
 */
object WeatherCache {
    private val KEY_JSON = stringPreferencesKey("weather_store_json")

    fun flow(context: Context): Flow<WeatherStore> =
        context.applicationContext.weatherDataStore.data.map { WeatherStoreCodec.decode(it[KEY_JSON]) }

    suspend fun load(context: Context): WeatherStore = flow(context).first()

    suspend fun save(context: Context, fresh: Map<String, WeatherSnapshot>) {
        if (fresh.isEmpty()) return
        context.applicationContext.weatherDataStore.edit { prefs ->
            val merged = WeatherStoreCodec.merge(WeatherStoreCodec.decode(prefs[KEY_JSON]), fresh)
            prefs[KEY_JSON] = WeatherStoreCodec.encode(merged)
        }
    }
}
