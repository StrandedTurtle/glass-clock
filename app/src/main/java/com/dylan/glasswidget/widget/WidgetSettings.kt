package com.dylan.glasswidget.widget

import androidx.datastore.preferences.core.Preferences
import com.dylan.glasswidget.data.CalendarEvent
import com.dylan.glasswidget.data.ClockFace
import com.dylan.glasswidget.data.CalendarLink
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
    val clockFace: ClockFace = ClockFace.Condensed,
    val variant: GlassVariant = GlassVariant.Soft,
    val tint: TintMode = TintMode.Frost,
    val alignment: WidgetAlignment = WidgetAlignment.Center,
    val textScale: Float = 1f,
    val clockScale: Float = 1f,
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
    val useDeviceCalendars: Boolean = true,
    val calendarLinks: List<CalendarLink> = emptyList(),
    val eventsAllDay: Boolean = true,
    val smartRain: Boolean = true,
    val smartAlarm: Boolean = true,
    val smartWarnings: Boolean = true,
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

    /** Cache tags of this widget's calendar links. */
    val linkSources: Set<String> get() = calendarLinks.map { it.source }.toSet()

    /** Events from the sources this widget has switched on. */
    fun visibleEvents(all: List<CalendarEvent>): List<CalendarEvent> =
        all.filter { if (it.fromLink) it.source in linkSources else useDeviceCalendars }

    companion object {
        fun from(p: Preferences) = WidgetSettings(
            clockApp = p[WidgetPrefsKeys.CLOCK_APP_PACKAGE],
            dateApp = p[WidgetPrefsKeys.DATE_APP_PACKAGE],
            weatherApp = p[WidgetPrefsKeys.WEATHER_APP_PACKAGE],
            clockStyle = ClockStyle.from(p[WidgetPrefsKeys.CLOCK_STYLE]),
            clockFace = ClockFace.from(p[WidgetPrefsKeys.CLOCK_FACE]),
            variant = GlassVariant.from(p[WidgetPrefsKeys.GLASS_VARIANT]),
            tint = TintMode.from(p[WidgetPrefsKeys.TINT_MODE]),
            alignment = WidgetAlignment.from(p[WidgetPrefsKeys.ALIGNMENT]),
            textScale = Limits.clampTextScale(p[WidgetPrefsKeys.TEXT_SCALE] ?: 1f),
            clockScale = Limits.clampClockScale(p[WidgetPrefsKeys.CLOCK_SCALE] ?: 1f),
            hourMode = HourMode.from(p[WidgetPrefsKeys.HOUR_MODE]),
            showColon = p[WidgetPrefsKeys.SHOW_COLON] ?: false,
            datePreset = DatePreset.from(p[WidgetPrefsKeys.DATE_FORMAT_PRESET]),
            tempUnit = TempUnit.from(p[WidgetPrefsKeys.TEMP_UNIT]),
            details = WeatherDetail.fromKeys(p[WidgetPrefsKeys.WEATHER_DETAILS]).let { saved ->
                // Lists saved before pollen, moon, tomorrow and hourly existed get them switched on once.
                if (p[WidgetPrefsKeys.WEATHER_DETAILS] != null && (p[WidgetPrefsKeys.WEATHER_DETAILS_REV] ?: 0) < 2) {
                    saved + setOf(WeatherDetail.Pollen, WeatherDetail.Moon, WeatherDetail.Tomorrow, WeatherDetail.Hourly)
                } else saved
            },
            locationMode = LocationMode.from(p[WidgetPrefsKeys.LOCATION_MODE]),
            cityLat = p[WidgetPrefsKeys.CITY_LAT],
            cityLon = p[WidgetPrefsKeys.CITY_LON],
            cityName = p[WidgetPrefsKeys.CITY_NAME],
            deviceLat = p[WidgetPrefsKeys.DEVICE_LAT],
            deviceLon = p[WidgetPrefsKeys.DEVICE_LON],
            showEvents = p[WidgetPrefsKeys.SHOW_EVENTS] ?: false,
            useDeviceCalendars = p[WidgetPrefsKeys.USE_DEVICE_CALENDARS] ?: true,
            calendarLinks = CalendarLink.decode(p[WidgetPrefsKeys.CALENDAR_LINKS]),
            eventsAllDay = p[WidgetPrefsKeys.EVENTS_ALL_DAY] ?: true,
            smartRain = p[WidgetPrefsKeys.SMART_RAIN] ?: true,
            smartAlarm = p[WidgetPrefsKeys.SMART_ALARM] ?: true,
            smartWarnings = p[WidgetPrefsKeys.SMART_WARNINGS] ?: true,
        )
    }
}
