package com.dylan.glasswidget.data

/** Pollen types Open-Meteo (CAMS) reports for Europe. */
enum class PollenType { Alder, Birch, Grass, Mugwort, Olive, Ragweed }

enum class PollenLevel { Low, Moderate, High, VeryHigh }

object Pollen {
    // Grains/m³ thresholds for Moderate, High and Very high, approximating the bands UK pollen
    // forecasts use (trees and weeds release differently to grass).
    private fun bands(type: PollenType): DoubleArray = when (type) {
        PollenType.Grass -> doubleArrayOf(30.0, 50.0, 150.0)
        PollenType.Alder, PollenType.Birch, PollenType.Olive -> doubleArrayOf(40.0, 80.0, 200.0)
        PollenType.Mugwort, PollenType.Ragweed -> doubleArrayOf(20.0, 50.0, 150.0)
    }

    fun level(r: PollenReading): PollenLevel {
        val b = bands(r.type)
        return when {
            r.grains >= b[2] -> PollenLevel.VeryHigh
            r.grains >= b[1] -> PollenLevel.High
            r.grains >= b[0] -> PollenLevel.Moderate
            else -> PollenLevel.Low
        }
    }

    /** The type that matters most right now (highest level, then highest count), or null with no data. */
    fun worst(readings: List<PollenReading>): Pair<PollenReading, PollenLevel>? =
        readings.map { it to level(it) }.maxWithOrNull(compareBy({ it.second }, { it.first.grains }))

    fun fromAirQuality(a: AirQualityBlock?): List<PollenReading> = if (a == null) emptyList() else listOfNotNull(
        a.alder?.let { PollenReading(PollenType.Alder, it) },
        a.birch?.let { PollenReading(PollenType.Birch, it) },
        a.grass?.let { PollenReading(PollenType.Grass, it) },
        a.mugwort?.let { PollenReading(PollenType.Mugwort, it) },
        a.olive?.let { PollenReading(PollenType.Olive, it) },
        a.ragweed?.let { PollenReading(PollenType.Ragweed, it) },
    )
}
