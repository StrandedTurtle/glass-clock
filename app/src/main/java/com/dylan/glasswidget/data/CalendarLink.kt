package com.dylan.glasswidget.data

/** A saved calendar link and the name it's shown under in settings. */
data class CalendarLink(val name: String, val url: String) {
    val source: String get() = Ics.sourceKey(url)

    companion object {
        /** One link per line as "name<TAB>url"; a bare URL (older format) gets its host as the name. */
        fun decode(text: String?): List<CalendarLink> =
            text.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
                val tab = line.indexOf('\t')
                if (tab > 0) CalendarLink(line.substring(0, tab).trim(), line.substring(tab + 1).trim())
                else CalendarLink(Ics.shortLabel(line), line)
            }.distinctBy { it.source }

        fun encode(links: List<CalendarLink>): String =
            links.joinToString("\n") { "${it.name.replace('\t', ' ').replace('\n', ' ')}\t${it.url.trim()}" }
    }
}
