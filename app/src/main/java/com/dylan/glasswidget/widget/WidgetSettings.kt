package com.dylan.glasswidget.widget

import androidx.datastore.preferences.core.Preferences
import com.dylan.glasswidget.data.ClockStyle
import com.dylan.glasswidget.data.DatePreset
import com.dylan.glasswidget.data.GlassVariant
import com.dylan.glasswidget.data.HourMode
import com.dylan.glasswidget.data.Limits
import com.dylan.glasswidget.data.Location
import com.dylan.glasswidget.data.LocationMode
import com.dylan.glasswidget.data.TempUnit
import com.dylan.glasswidget.data.TintMode
import com.dylan.glasswidget.data.WeatherDetail
import com.dylan.glasswidget.data.WidgetAlignment

/** Typed, defaulted view of one widget's preferences. A blank widget already looks right. */
data class WidgetSettings(
    val clockApp: String? = null,
    val dateApp: String? = null,
    val weatherApp: String? = null,
    val clockStyle: ClockStyle = ClockStyle.Glass,
    val variant: GlassVariant = GlassVariant.Soft,
    val tint: TintMode = TintMode.Frost,
    val alignment: WidgetAlignment = WidgetAlignment.Center,
    val textScale: Float = 1f,
    val hourMode: HourMode = HourMode.System,
    val showColon: Boolean = false,
    val datePreset: DatePreset = DatePreset.Short,
    val tempUnit: TempUnit = TempUnit.C,
    val details: Set<WeatherDetail> = WeatherDetail.defaults,
    val locationMode: LocationMode = LocationMode.City,
    val cityLat: Double? = null,
    val cityLon: Double? = null,
    val cityName: String? = null,
    val deviceLat: Double? = null,
    val deviceLon: Double? = null,
    val showEvents: Boolean = false,
    val eventsAllDay: Boolean = true,
) {
    /** Where this widget wants weather for, or null if the user hasn't picked anywhere yet. */
    val location: Location?
        get() = when (locationMode) {
            LocationMode.City ->
                if (cityLat != null && cityLon != null) Location(cityLat, cityLon, cityName) else null
            LocationMode.Device ->
                if (deviceLat != null && deviceLon != null) Location(deviceLat, deviceLon, null) else null
        }

    val fahrenheit: Boolean get() = tempUnit == TempUnit.F

    companion object {
        fun from(p: Preferences) = WidgetSettings(
            clockApp = p[WidgetPrefsKeys.CLOCK_APP_PACKAGE],
            dateApp = p[WidgetPrefsKeys.DATE_APP_PACKAGE],
            weatherApp = p[WidgetPrefsKeys.WEATHER_APP_PACKAGE],
            clockStyle = ClockStyle.from(p[WidgetPrefsKeys.CLOCK_STYLE]),
            variant = GlassVariant.from(p[WidgetPrefsKeys.GLASS_VARIANT]),
            tint = TintMode.from(p[WidgetPrefsKeys.TINT_MODE]),
            alignment = WidgetAlignment.from(p[WidgetPrefsKeys.ALIGNMENT]),
            textScale = Limits.clampTextScale(p[WidgetPrefsKeys.TEXT_SCALE] ?: 1f),
            hourMode = HourMode.from(p[WidgetPrefsKeys.HOUR_MODE]),
            showColon = p[WidgetPrefsKeys.SHOW_COLON] ?: false,
            datePreset = DatePreset.from(p[WidgetPrefsKeys.DATE_FORMAT_PRESET]),
            tempUnit = TempUnit.from(p[WidgetPrefsKeys.TEMP_UNIT]),
            details = WeatherDetail.fromKeys(p[WidgetPrefsKeys.WEATHER_DETAILS]),
            locationMode = LocationMode.from(p[WidgetPrefsKeys.LOCATION_MODE]),
            cityLat = p[WidgetPrefsKeys.CITY_LAT],
            cityLon = p[WidgetPrefsKeys.CITY_LON],
            cityName = p[WidgetPrefsKeys.CITY_NAME],
            deviceLat = p[WidgetPrefsKeys.DEVICE_LAT],
            deviceLon = p[WidgetPrefsKeys.DEVICE_LON],
            showEvents = p[WidgetPrefsKeys.SHOW_EVENTS] ?: false,
            eventsAllDay = p[WidgetPrefsKeys.EVENTS_ALL_DAY] ?: true,
        )
    }
}
