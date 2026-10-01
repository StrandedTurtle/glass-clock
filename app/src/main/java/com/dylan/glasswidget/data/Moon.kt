package com.dylan.glasswidget.data

import kotlin.math.floor

enum class MoonPhase { New, WaxingCrescent, FirstQuarter, WaxingGibbous, Full, WaningGibbous, LastQuarter, WaningCrescent }

/** Moon phase from a known new moon and the mean synodic month; accurate to within about a day. */
object Moon {
    private const val SYNODIC_DAYS = 29.530588853
    private const val KNOWN_NEW_MOON_MS = 947_182_440_000L // 2000-01-06T18:14:00Z
    private const val DAY_MS = 86_400_000.0

    /** 0 = new, 0.5 = full, back to 1. */
    fun age(epochMs: Long): Double {
        val cycles = (epochMs - KNOWN_NEW_MOON_MS) / DAY_MS / SYNODIC_DAYS
        return cycles - floor(cycles)
    }

    fun phase(epochMs: Long): MoonPhase = MoonPhase.entries[(floor(age(epochMs) * 8 + 0.5).toInt()) % 8]
}
