package com.dylan.glasswidget.data

import kotlin.math.roundToInt

fun celsiusToFahrenheit(c: Double): Double = c * 9.0 / 5.0 + 32.0

/** "21°" — rounded, in the requested unit, never "-0°". */
fun formatTemp(celsius: Double, fahrenheit: Boolean): String {
    val v = if (fahrenheit) celsiusToFahrenheit(celsius) else celsius
    val rounded = v.roundToInt()
    return "${if (rounded == 0) 0 else rounded}°"
}
