package com.tkolymp.shared.viewmodels

import com.tkolymp.shared.people.Person
import com.tkolymp.shared.utils.birthdayMonthDay
import com.tkolymp.shared.utils.daysUntilNextBirthday
import com.tkolymp.shared.utils.formatBirthDateString
import com.tkolymp.shared.utils.turningAgeOnNextBirthday
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

fun birthdayEntryFor(p: Person): BirthdayEntry {
    val name = buildList {
        p.prefixTitle?.takeIf { it.isNotBlank() }?.let { add(it) }
        p.firstName?.takeIf { it.isNotBlank() }?.let { add(it) }
        p.lastName?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToString(" ").let { base ->
        if (!p.suffixTitle.isNullOrBlank()) "$base, ${p.suffixTitle}" else base.ifBlank { p.id }
    }
    val cohortColors = p.cohortMembershipsList
        .mapNotNull { it.cohort }
        .filter { it.isVisible != false }
        .mapNotNull { it.colorRgb }
        .filter { it.isNotBlank() }
    return BirthdayEntry(
        personId = p.id,
        name = name,
        formattedBirthDate = formatBirthDateString(p.birthDate),
        days = daysUntilNextBirthday(p.birthDate),
        turningAge = turningAgeOnNextBirthday(p.birthDate),
        birthYear = p.birthDate?.trim()?.take(4)?.toIntOrNull(),
        cohortColors = cohortColors
    )
}

/** Groups people whose birth month/day falls on one of [dates] (year-agnostic match). */
fun groupBirthdaysByDate(people: List<Person>, dates: List<String>): Map<String, List<BirthdayEntry>> {
    val dateMonthDays = dates.mapNotNull { d ->
        try { val ld = LocalDate.parse(d); d to (ld.month.number to ld.day) } catch (_: Exception) { null }
    }
    if (dateMonthDays.isEmpty()) return emptyMap()

    val result = mutableMapOf<String, MutableList<BirthdayEntry>>()
    for (p in people) {
        val md = birthdayMonthDay(p.birthDate) ?: continue
        val matchingDate = dateMonthDays.firstOrNull { it.second == md }?.first ?: continue
        result.getOrPut(matchingDate) { mutableListOf() }.add(birthdayEntryFor(p))
    }
    return result
}
