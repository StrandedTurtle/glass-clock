package com.dylan.glasswidget.data

import kotlin.math.roundToInt

fun celsiusToFahrenheit(c: Double): Double = c * 9.0 / 5.0 + 32.0

/** "21°" — rounded, in the requested unit, never "-0°". */
fun formatTemp(celsius: Double, fahrenheit: Boolean): String {
    val v = if (fahrenheit) celsiusToFahrenheit(celsius) else celsius
    val rounded = v.roundToInt()
    return "${if (rounded == 0) 0 else rounded}°"
}

/** "12 km/h" or, alongside °F, "7 mph". */
fun formatWind(kmh: Double, imperial: Boolean): String =
    if (imperial) "${(kmh / 1.609344).roundToInt()} mph" else "${kmh.roundToInt()} km/h"

/** UV rounds half-up to a whole index like every forecast shows it. */
fun formatUv(uv: Double): String = "UV ${uv.roundToInt()}"
