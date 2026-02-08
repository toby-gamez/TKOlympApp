package com.tkolymp.tkolympapp

import com.tkolymp.shared.event.Event
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

fun translateEventType(type: String?): String? {
    if (type.isNullOrBlank()) return null
    return when (type.trim().lowercase()) {
        "group" -> "společná"
        "lesson" -> "lekce"
        "holiday" -> "prázdniny"
        "rezervation" -> "nabídka"
        "camp" -> "soustředění"
        else -> type
    }
}

fun formatTimes(since: String?, until: String?): String {
    if (since.isNullOrBlank() && until.isNullOrBlank()) return ""
    fun fmtTime(s: String?): String? {
        val ldt = parseToLocal(s) ?: return null
        val hh = ldt.hour.toString().padStart(2, '0')
        val mm = ldt.minute.toString().padStart(2, '0')
        return "$hh:$mm"
    }

    val a = fmtTime(since)
    val b = fmtTime(until)
    return when {
        a != null && b != null -> "$a - $b"
        a != null -> a
        b != null -> b
        else -> ""
    }
}

fun formatTimesWithDate(since: String?, until: String?): String {
    if (since.isNullOrBlank() && until.isNullOrBlank()) return ""

    fun parseDateTime(s: String?): Pair<String?, String?>? {
        val ldt = parseToLocal(s) ?: return null
        val date = "${ldt.date.dayOfMonth}.${ldt.date.monthNumber}.${ldt.date.year}"
        val time = "${ldt.hour.toString().padStart(2, '0')}:${ldt.minute.toString().padStart(2, '0')}"
        return Pair(date, time)
    }

    val sinceData = parseDateTime(since)
    val untilData = parseDateTime(until)

    return when {
        sinceData != null && untilData != null -> {
            val (sinceDate, sinceTime) = sinceData
            val (untilDate, untilTime) = untilData
            if (sinceDate == untilDate) {
                // Stejný den - zobraz jen časy bez data
                "$sinceTime - $untilTime"
            } else {
                // Různé dny - zobraz s daty
                "$sinceDate $sinceTime - $untilDate $untilTime"
            }
        }
        sinceData != null -> "${sinceData.first} ${sinceData.second}"
        untilData != null -> "${untilData.first} ${untilData.second}"
        else -> ""
    }
}

fun parseToLocal(s: String?): LocalDateTime? {
    if (s.isNullOrBlank()) return null
    return try {
        val instant = Instant.parse(s)
        instant.toLocalDateTime(TimeZone.currentSystemDefault())
    } catch (_: Exception) {
        try {
            LocalDateTime.parse(s)
        } catch (_: Exception) {
            null
        }
    }
}

fun formatTimesWithDateAlways(since: String?, until: String?): String {
    if (since.isNullOrBlank() && until.isNullOrBlank()) return ""

    fun parseDateTime(s: String?): Pair<String?, String?>? {
        val ldt = parseToLocal(s) ?: return null
        val date = "${ldt.date.dayOfMonth}.${ldt.date.monthNumber}.${ldt.date.year}"
        val time = "${ldt.hour.toString().padStart(2, '0')}:${ldt.minute.toString().padStart(2, '0')}"
        return Pair(date, time)
    }

    val sinceData = parseDateTime(since)
    val untilData = parseDateTime(until)

    return when {
        sinceData != null && untilData != null -> {
            val (sinceDate, sinceTime) = sinceData
            val (untilDate, untilTime) = untilData
            if (sinceDate == untilDate) {
                // Stejný den - ale stále zobraz datum
                "$sinceDate $sinceTime - $untilTime"
            } else {
                // Různé dny - zobraz oba daty
                "$sinceDate $sinceTime - $untilDate $untilTime"
            }
        }
        sinceData != null -> "${sinceData.first} ${sinceData.second}"
        untilData != null -> "${untilData.first} ${untilData.second}"
        else -> ""
    }
}

fun durationMinutes(since: String?, until: String?): String? {
    if (since.isNullOrBlank() || until.isNullOrBlank()) return null
    return try {
        val aInstant = try { Instant.parse(since) } catch (_: Exception) { null }
        val bInstant = try { Instant.parse(until) } catch (_: Exception) { null }
        if (aInstant != null && bInstant != null) {
            val secs = bInstant.epochSeconds - aInstant.epochSeconds
            val mins = secs / 60
            "${mins}'"
        } else {
            // Fallback: try parsing as local datetimes and compute difference
            val aLocal = try { LocalDateTime.parse(since) } catch (_: Exception) { null }
            val bLocal = try { LocalDateTime.parse(until) } catch (_: Exception) { null }
            if (aLocal != null && bLocal != null) {
                val aZ = aLocal.toInstant(TimeZone.currentSystemDefault())
                val bZ = bLocal.toInstant(TimeZone.currentSystemDefault())
                val secs = bZ.epochSeconds - aZ.epochSeconds
                val mins = secs / 60
                "${mins}'"
            } else null
        }
    } catch (_: Exception) {
        null
    }
}

fun formatHtmlContent(html: String?): String {
    if (html.isNullOrBlank()) return ""
    
    var text = html
        // Nahradit odstavce a breaks novými řádky
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<p[^>]*>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
        
        // Nadpisy
        .replace(Regex("<h[1-6][^>]*>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</h[1-6]>", RegexOption.IGNORE_CASE), "\n\n")
        
        // Seznamy
        .replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "\n• ")
        .replace(Regex("</li>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("</?[ou]l[^>]*>", RegexOption.IGNORE_CASE), "\n")
        
        // Formátovací tagy - zachovat obsah, odstranit tagy
        .replace(Regex("</?strong>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("</?b>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("</?em>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("</?i>", RegexOption.IGNORE_CASE), "")
    
    // Extrahovat odkazy před odstraněním HTML tagů
    text = text.replace(Regex("<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>([^<]*)</a>", RegexOption.IGNORE_CASE)) { matchResult ->
        val url = matchResult.groupValues[1]
        val linkText = matchResult.groupValues[2]
        if (linkText.isNotBlank()) {
            "$linkText ($url)"
        } else {
            url
        }
    }
    
    text = text
        // Odstranit zbylé HTML tagy
        .replace(Regex("<[^>]+>"), "")
        
        // Dekódovat HTML entity
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        
        // Vyčistit nadbytečné prázdné řádky
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
    
    return text
}

fun participantsForEvent(event: Event?): List<String> {
    if (event == null) return emptyList()
    val regs = event.eventRegistrationsList
    if (regs.isEmpty()) return emptyList()
    return regs.mapNotNull { r ->
        r.person?.name ?: run {
            val man = r.couple?.man
            val woman = r.couple?.woman
            if (man != null && woman != null) {
                val manSurname = man.lastName?.takeIf { it.isNotBlank() } ?: man.firstName?.takeIf { it.isNotBlank() } ?: ""
                val womanSurname = woman.lastName?.takeIf { it.isNotBlank() } ?: woman.firstName?.takeIf { it.isNotBlank() } ?: ""
                val pair = listOfNotNull(manSurname.takeIf { it.isNotBlank() }, womanSurname.takeIf { it.isNotBlank() }).joinToString(" - ")
                if (pair.isNotBlank()) pair else null
            } else null
        }
    }
}
