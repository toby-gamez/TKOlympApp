package com.tkolymp.tkolympapp

import com.tkolymp.shared.event.Event
import java.time.OffsetDateTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
        if (s.isNullOrBlank()) return null
        return try {
            val odt = OffsetDateTime.parse(s)
            odt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
        } catch (_: Exception) {
            try {
                val ldt = LocalDateTime.parse(s)
                ldt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            } catch (_: Exception) {
                val t = s.substringAfter('T', "").substringBefore('Z').substringBefore('+').substringBefore('-')
                if (t.isBlank()) null else t
            }
        }
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
        if (s.isNullOrBlank()) return null
        return try {
            val odt = OffsetDateTime.parse(s)
            val date = odt.toLocalDate().format(DateTimeFormatter.ofPattern("d.M.yyyy"))
            val time = odt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            Pair(date, time)
        } catch (_: Exception) {
            try {
                val ldt = LocalDateTime.parse(s)
                val date = ldt.toLocalDate().format(DateTimeFormatter.ofPattern("d.M.yyyy"))
                val time = ldt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
                Pair(date, time)
            } catch (_: Exception) {
                null
            }
        }
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

fun formatTimesWithDateAlways(since: String?, until: String?): String {
    if (since.isNullOrBlank() && until.isNullOrBlank()) return ""

    fun parseDateTime(s: String?): Pair<String?, String?>? {
        if (s.isNullOrBlank()) return null
        return try {
            val odt = OffsetDateTime.parse(s)
            val date = odt.toLocalDate().format(DateTimeFormatter.ofPattern("d.M.yyyy"))
            val time = odt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            Pair(date, time)
        } catch (_: Exception) {
            try {
                val ldt = LocalDateTime.parse(s)
                val date = ldt.toLocalDate().format(DateTimeFormatter.ofPattern("d.M.yyyy"))
                val time = ldt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
                Pair(date, time)
            } catch (_: Exception) {
                null
            }
        }
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
        val a = OffsetDateTime.parse(since)
        val b = OffsetDateTime.parse(until)
        val mins = java.time.Duration.between(a, b).toMinutes()
        "${mins}'"
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
