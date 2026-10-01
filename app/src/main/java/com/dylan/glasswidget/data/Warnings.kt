package com.dylan.glasswidget.data

import kotlin.math.cos
import kotlin.math.hypot

enum class WarningLevel { Yellow, Amber, Red }

/**
 * UK Met Office severe weather warnings, from its public RSS feed per region:
 * https://www.metoffice.gov.uk/public/data/PWSCache/WarningsRSS/Region/<code>
 */
object MetOfficeWarnings {
    data class Region(val code: String, val name: String, val points: List<Pair<Double, Double>>)

    // Rough centres (some regions need two) for picking the region from a location.
    val regions = listOf(
        Region("os", "Orkney & Shetland", listOf(59.0 to -3.0, 60.3 to -1.3)),
        Region("he", "Highlands & Eilean Siar", listOf(57.5 to -5.0)),
        Region("gr", "Grampian", listOf(57.2 to -2.5)),
        Region("ta", "Central, Tayside & Fife", listOf(56.4 to -3.5)),
        Region("st", "Strathclyde", listOf(55.8 to -4.6)),
        Region("dg", "SW Scotland, Lothian & Borders", listOf(55.6 to -3.2, 55.0 to -4.0)),
        Region("ni", "Northern Ireland", listOf(54.6 to -6.7)),
        Region("wl", "Wales", listOf(52.4 to -3.7, 51.6 to -3.4, 53.1 to -3.8)),
        Region("nw", "North West England", listOf(54.0 to -2.7, 53.45 to -2.5)),
        Region("ne", "North East England", listOf(54.9 to -1.7)),
        Region("yh", "Yorkshire & Humber", listOf(53.9 to -1.2)),
        Region("wm", "West Midlands", listOf(52.5 to -2.2)),
        Region("em", "East Midlands", listOf(52.9 to -0.9)),
        Region("ee", "East of England", listOf(52.3 to 0.6)),
        Region("sw", "South West England", listOf(50.6 to -4.0, 51.3 to -2.6)),
        Region("se", "London & South East England", listOf(51.3 to -0.4)),
    )

    fun feedUrl(code: String) = "https://www.metoffice.gov.uk/public/data/PWSCache/WarningsRSS/Region/$code"

    fun byCode(code: String?): Region? = regions.firstOrNull { it.code == code }

    /** The warning region for a location, or null outside the UK. */
    fun regionFor(lat: Double, lon: Double): Region? {
        if (lat !in 49.8..61.0 || lon !in -8.8..2.0) return null
        val k = cos(Math.toRadians(lat))
        return regions.minByOrNull { r -> r.points.minOf { (a, b) -> hypot(lat - a, (lon - b) * k) } }
    }

    private val TITLE = Regex("""\b(Yellow|Amber|Red)\s+warning\s+of\s+(.+?)(?:\s+affecting\b|$)""", RegexOption.IGNORE_CASE)

    /** Warnings in a region feed, worst first, one per hazard. Items look like "Yellow warning of rain affecting …". */
    fun parseRss(xml: String): List<WeatherWarning> {
        val titles = Regex("""<item\b[\s\S]*?</item>""").findAll(xml).mapNotNull { item ->
            Regex("""<title>\s*(?:<!\[CDATA\[)?([\s\S]*?)(?:]]>)?\s*</title>""").find(item.value)?.groupValues?.get(1)
        }
        return titles.mapNotNull { title ->
            val m = TITLE.find(title.replace("&amp;", "&")) ?: return@mapNotNull null
            val level = WarningLevel.entries.first { it.name.equals(m.groupValues[1], true) }
            WeatherWarning(level, m.groupValues[2].trim().lowercase())
        }.toList()
            .groupBy { it.hazard }.map { (_, same) -> same.maxBy { it.level } }
            .sortedByDescending { it.level }
    }
}
