package com.dylan.glasswidget.widget

import androidx.datastore.preferences.core.Preferences
import com.dylan.glasswidget.data.DatePreset
import com.dylan.glasswidget.data.GlassVariant
import com.dylan.glasswidget.data.HourMode
import com.dylan.glasswidget.data.Limits
import com.dylan.glasswidget.data.Location
import com.dylan.glasswidget.data.LocationMode
import com.dylan.glasswidget.data.PaddingMode
import com.dylan.glasswidget.data.TempUnit
import com.dylan.glasswidget.data.TintMode

/** Typed, defaulted view of one widget's preferences. A blank widget already looks right. */
data class WidgetSettings(
    val clockApp: String? = null,
    val dateApp: String? = null,
    val weatherApp: String? = null,
    val textScale: Float = 1f,
    val padding: PaddingMode = PaddingMode.Normal,
    val variant: GlassVariant = GlassVariant.Soft,
    val cornerRadiusDp: Int = Limits.DEFAULT_RADIUS,
    val tint: TintMode = TintMode.Dynamic,
    val hourMode: HourMode = HourMode.System,
    val datePreset: DatePreset = DatePreset.Short,
    val tempUnit: TempUnit = TempUnit.C,
    val locationMode: LocationMode = LocationMode.City,
    val cityLat: Double? = null,
    val cityLon: Double? = null,
    val cityName: String? = null,
    val deviceLat: Double? = null,
    val deviceLon: Double? = null,
) {
    /** Where this widget wants weather for, or null if the user hasn't picked anywhere yet. */
    val location: Location?
        get() = when (locationMode) {
            LocationMode.City ->
                if (cityLat != null && cityLon != null) Location(cityLat, cityLon, cityName) else null
            LocationMode.Device ->
                if (deviceLat != null && deviceLon != null) Location(deviceLat, deviceLon, null) else null
        }

    companion object {
        fun from(p: Preferences) = WidgetSettings(
            clockApp = p[WidgetPrefsKeys.CLOCK_APP_PACKAGE],
            dateApp = p[WidgetPrefsKeys.DATE_APP_PACKAGE],
            weatherApp = p[WidgetPrefsKeys.WEATHER_APP_PACKAGE],
            textScale = Limits.clampTextScale(p[WidgetPrefsKeys.TEXT_SCALE] ?: 1f),
            padding = PaddingMode.from(p[WidgetPrefsKeys.PADDING_MODE]),
            variant = GlassVariant.from(p[WidgetPrefsKeys.GLASS_VARIANT]),
            cornerRadiusDp = Limits.snapRadius(p[WidgetPrefsKeys.CORNER_RADIUS] ?: Limits.DEFAULT_RADIUS),
            tint = TintMode.from(p[WidgetPrefsKeys.TINT_MODE]),
            hourMode = HourMode.from(p[WidgetPrefsKeys.HOUR_MODE]),
            datePreset = DatePreset.from(p[WidgetPrefsKeys.DATE_FORMAT_PRESET]),
            tempUnit = TempUnit.from(p[WidgetPrefsKeys.TEMP_UNIT]),
            locationMode = LocationMode.from(p[WidgetPrefsKeys.LOCATION_MODE]),
            cityLat = p[WidgetPrefsKeys.CITY_LAT],
            cityLon = p[WidgetPrefsKeys.CITY_LON],
            cityName = p[WidgetPrefsKeys.CITY_NAME],
            deviceLat = p[WidgetPrefsKeys.DEVICE_LAT],
            deviceLon = p[WidgetPrefsKeys.DEVICE_LON],
        )
    }
}
