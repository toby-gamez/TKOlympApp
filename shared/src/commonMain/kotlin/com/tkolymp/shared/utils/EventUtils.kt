package com.tkolymp.shared.utils

import com.tkolymp.shared.event.Event
import com.tkolymp.shared.language.AppStrings
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock

fun translateEventType(type: String?): String? {
    if (type.isNullOrBlank()) return null
    val s = AppStrings.current.events // Accessing the EventStrings group
    return when (type.trim().lowercase()) {
        "personal" -> AppStrings.current.personalEvents.defaultTitle
        "group" -> s.eventTypeGroup
        "lesson" -> s.eventTypeLesson
        "holiday" -> s.eventTypeHoliday
        "rezervation", "reservation" -> s.eventTypeReservation
        "camp" -> s.eventTypeCamp
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
                "$sinceTime - $untilTime"
            } else {
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
                "$sinceDate $sinceTime - $untilTime"
            } else {
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
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<p[^>]*>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
        .replace(Regex("<h[1-6][^>]*>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</h[1-6]>", RegexOption.IGNORE_CASE), "\n\n")
        .replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "\n• ")
        .replace(Regex("</li>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("</?[ou]l[^>]*>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</?strong>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("</?b>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("</?em>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("</?i>", RegexOption.IGNORE_CASE), "")
    
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
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
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

fun daysUntilNextBirthday(raw: String?): Int {
    if (raw.isNullOrBlank()) return Int.MAX_VALUE
    val s = raw.trim()
    val datePrefix = Regex("\\d{4}-\\d{2}-\\d{2}").find(s)?.value
    val ld = if (datePrefix != null) {
        try { LocalDate.parse(datePrefix) } catch (_: Exception) { null }
    } else {
        parseToLocal(s)?.date
    } ?: return Int.MAX_VALUE

    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val month = ld.monthNumber
    val day = ld.dayOfMonth
    val candidate = try {
        LocalDate(today.year, month, day)
    } catch (_: Exception) {
        if (month == 2 && day == 29) LocalDate(today.year, 2, 28) else return Int.MAX_VALUE
    }

    var next = if (candidate < today) candidate.plus(1, DateTimeUnit.YEAR) else candidate
    if (next.monthNumber == 2 && next.dayOfMonth == 29) {
        val y = next.year
        val isLeap = (y % 4 == 0 && (y % 100 != 0 || y % 400 == 0))
        if (!isLeap) next = LocalDate(y, 2, 28)
    }
    return (next.toEpochDays() - today.toEpochDays()).toInt()
}

fun formatBirthDateString(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val s = raw.trim()
    val datePrefix = Regex("\\d{4}-\\d{2}-\\d{2}").find(s)?.value
    val ld = if (datePrefix != null) {
        try { LocalDate.parse(datePrefix) } catch (_: Exception) { null }
    } else {
        parseToLocal(s)?.date
    } ?: return null
    return formatShortDate(ld)
}

fun formatTimeAgo(epochMs: Long, nowMs: Long = Clock.System.now().toEpochMilliseconds()): String {
    val mins = ((nowMs - epochMs) / 60000).coerceAtLeast(0)
    return if (mins < 60) {
        AppStrings.current.notifications.timeAgoMinutes.replace("{0}", mins.toString())
    } else {
        AppStrings.current.notifications.timeAgoHours.replace("{0}", (mins / 60).toString())
    }
}
