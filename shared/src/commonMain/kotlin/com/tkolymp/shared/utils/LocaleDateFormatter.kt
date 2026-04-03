package com.tkolymp.shared.utils

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/**
 * Locale-aware date formatting for Compose Multiplatform commonMain.
 * Replaces java.time.DateTimeFormatter.ofPattern(..., locale) usage.
 */

private val MONTH_NAMES: Map<String, List<String>> = mapOf(
    "cs" to listOf(
        "ledna", "února", "března", "dubna", "května", "června",
        "července", "srpna", "září", "října", "listopadu", "prosince"
    ),
    "de" to listOf(
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember"
    ),
    "sk" to listOf(
        "januára", "februára", "marca", "apríla", "mája", "júna",
        "júla", "augusta", "septembra", "októbra", "novembra", "decembra"
    ),
    "sl" to listOf(
        "januarja", "februarja", "marca", "aprila", "maja", "junija",
        "julija", "avgusta", "septembra", "oktobra", "novembra", "decembra"
    ),
    "ua" to listOf(
        "січня", "лютого", "березня", "квітня", "травня", "червня",
        "липня", "серпня", "вересня", "жовтня", "листопада", "грудня"
    ),
    "vi" to listOf(
        "Tháng 1", "Tháng 2", "Tháng 3", "Tháng 4", "Tháng 5", "Tháng 6",
        "Tháng 7", "Tháng 8", "Tháng 9", "Tháng 10", "Tháng 11", "Tháng 12"
    ),
    "en" to listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    ),
    "brainrot" to listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
)

/** Nominative month names for cases where we display "Month Year" (e.g. "září 2025"). */
private val MONTH_NAMES_NOMINATIVE: Map<String, List<String>> = mapOf(
    "cs" to listOf(
        "leden", "únor", "březen", "duben", "květen", "červen",
        "červenec", "srpen", "září", "říjen", "listopad", "prosinec"
    ),
    "de" to listOf(
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember"
    ),
    "sk" to listOf(
        "január", "február", "marec", "apríl", "máj", "jún",
        "júl", "august", "september", "október", "november", "december"
    ),
    "sl" to listOf(
        "januar", "februar", "marec", "april", "maj", "junij",
        "julij", "avgust", "september", "oktober", "november", "december"
    ),
    "ua" to listOf(
        "січень", "лютий", "березень", "квітень", "травень", "червень",
        "липень", "серпень", "вересень", "жовтень", "листопад", "грудень"
    ),
    "vi" to listOf(
        "Tháng 1", "Tháng 2", "Tháng 3", "Tháng 4", "Tháng 5", "Tháng 6",
        "Tháng 7", "Tháng 8", "Tháng 9", "Tháng 10", "Tháng 11", "Tháng 12"
    ),
    "en" to listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    ),
    "brainrot" to listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
)

private val DAY_NAMES_FULL: Map<String, List<String>> = mapOf(
    "cs" to listOf("pondělí", "úterý", "středa", "čtvrtek", "pátek", "sobota", "neděle"),
    "de" to listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag"),
    "sk" to listOf("pondelok", "utorok", "streda", "štvrtok", "piatok", "sobota", "nedeľa"),
    "sl" to listOf("ponedeljek", "torek", "sreda", "četrtek", "petek", "sobota", "nedelja"),
    "ua" to listOf("понеділок", "вівторок", "середа", "четвер", "п'ятниця", "субота", "неділя"),
    "vi" to listOf("Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật"),
    "en" to listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"),
    "brainrot" to listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
)

/** Returns localized month name (genitive form) for the 1-based month number. */
fun getLocalizedMonthName(monthNumber: Int, languageCode: String): String {
    val months = MONTH_NAMES[languageCode.lowercase()] ?: MONTH_NAMES["en"]!!
    return months.getOrElse(monthNumber - 1) { monthNumber.toString() }
}

/** Returns the nominative/localized month name for displaying "Month Year". */
fun getLocalizedMonthNameNominative(monthNumber: Int, languageCode: String): String {
    val months = MONTH_NAMES_NOMINATIVE[languageCode.lowercase()] ?: MONTH_NAMES_NOMINATIVE["en"]!!
    return months.getOrElse(monthNumber - 1) { monthNumber.toString() }
}

/** Returns localized full day-of-week name (Monday = index 0). */
fun getLocalizedDayName(dayOfWeek: DayOfWeek, languageCode: String): String {
    val days = DAY_NAMES_FULL[languageCode.lowercase()] ?: DAY_NAMES_FULL["en"]!!
    // DayOfWeek.ordinal: MONDAY=0 … SUNDAY=6 in kotlinx.datetime
    return days.getOrElse(dayOfWeek.ordinal) { dayOfWeek.name }
}

/**
 * Formats a date as "d. MMMM" or "d. MMMM yyyy" with locale-aware month name.
 * Equivalent to DateTimeFormatter.ofPattern("d. MMMM [yyyy]", locale).
 */
fun formatMonthDay(date: LocalDate, languageCode: String, includeYear: Boolean): String {
    val month = getLocalizedMonthName(date.monthNumber, languageCode)
    return if (includeYear) "${date.dayOfMonth}. $month ${date.year}"
    else "${date.dayOfMonth}. $month"
}

/**
 * Formats a date as "EEEE, d. MMMM" or "EEEE, d. MMMM yyyy" with locale-aware names.
 * Equivalent to DateTimeFormatter.ofPattern("EEEE, d. MMMM [yyyy]", locale).
 */
fun formatFullCalendarDate(date: LocalDate, languageCode: String, includeYear: Boolean): String {
    val dayName = getLocalizedDayName(date.dayOfWeek, languageCode)
    val month = getLocalizedMonthName(date.monthNumber, languageCode)
    return if (includeYear) "$dayName, ${date.dayOfMonth}. $month ${date.year}"
    else "$dayName, ${date.dayOfMonth}. $month"
}

/**
 * Short numeric date "d. M. yyyy" – no locale required.
 */
fun formatShortDate(date: LocalDate): String =
    "${date.dayOfMonth}. ${date.monthNumber}. ${date.year}"

/**
 * Short numeric date-time "d. M. yyyy HH:mm".
 */
fun formatShortDateTime(date: LocalDate, hour: Int, minute: Int): String =
    "${date.dayOfMonth}. ${date.monthNumber}. ${date.year} ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
