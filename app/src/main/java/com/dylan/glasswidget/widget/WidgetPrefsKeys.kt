package com.dylan.glasswidget.widget

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

/** Every per-widget preference key, in one place. Values are parsed by [WidgetSettings]. */
object WidgetPrefsKeys {
    // Tap targets. Absent = use the default app; "" = deliberately nothing.
    val CLOCK_APP_PACKAGE = stringPreferencesKey("clock_app_package")
    val DATE_APP_PACKAGE = stringPreferencesKey("date_app_package")
    val WEATHER_APP_PACKAGE = stringPreferencesKey("weather_app_package")

    // Look
    val CLOCK_STYLE = stringPreferencesKey("clock_style")
    val GLASS_VARIANT = stringPreferencesKey("glass_variant")
    val TINT_MODE = stringPreferencesKey("tint_mode")
    val ALIGNMENT = stringPreferencesKey("alignment")
    val TEXT_SCALE = floatPreferencesKey("text_scale")    // pill text
    val CLOCK_SCALE = floatPreferencesKey("clock_scale")  // digits

    // Clock & date
    val HOUR_MODE = stringPreferencesKey("hour_mode")
    val SHOW_COLON = booleanPreferencesKey("show_colon")
    val DATE_FORMAT_PRESET = stringPreferencesKey("date_format_preset")

    // Weather
    val TEMP_UNIT = stringPreferencesKey("temp_unit")
    val WEATHER_DETAILS = stringSetPreferencesKey("weather_details")
    val LOCATION_MODE = stringPreferencesKey("location_mode")
    val CITY_LAT = doublePreferencesKey("city_lat")
    val CITY_LON = doublePreferencesKey("city_lon")
    val CITY_NAME = stringPreferencesKey("city_name")
    // Last known device fix, refreshed whenever the app (foreground) or the worker can read one.
    val DEVICE_LAT = doublePreferencesKey("device_lat")
    val DEVICE_LON = doublePreferencesKey("device_lon")

    // Calendar
    val SHOW_EVENTS = booleanPreferencesKey("show_events")
    val EVENTS_ALL_DAY = booleanPreferencesKey("events_all_day")
}
