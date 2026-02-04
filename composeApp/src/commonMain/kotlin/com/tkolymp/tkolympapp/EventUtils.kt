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
