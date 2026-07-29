package com.tkolymp.shared.campschedule

import com.tkolymp.shared.people.PersonDetails

enum class Gender { MALE, FEMALE }

/**
 * The name that will appear in the photographed table for this person: for a couple,
 * the table always lists the man's surname regardless of which partner is viewing;
 * solo participants are listed by their own first name in individual-lesson columns.
 * A woman in a couple can still have solo lessons under her own first name, so her
 * primary name resolves to that instead — [resolvePartnerName] supplies the couple's
 * surname separately, to be searched alongside it.
 */
fun resolveMyTableName(personDetails: PersonDetails, gender: Gender? = null): String {
    if (gender == Gender.FEMALE) return personDetails.firstName.orEmpty()
    return personDetails.activeCouplesList.firstOrNull()?.man?.lastName?.takeIf { it.isNotBlank() }
        ?: personDetails.firstName.orEmpty()
}

/**
 * My active couple's man's surname, to search in addition to [resolveMyTableName] when
 * I'm the woman — couple lessons are listed under his surname, but my own solo lessons
 * are listed under my first name, so both must be searched. Empty for a man (his own
 * name already covers couple lessons) or when there's no active couple.
 */
fun resolvePartnerName(personDetails: PersonDetails, gender: Gender?): String {
    if (gender != Gender.FEMALE) return ""
    return personDetails.activeCouplesList.firstOrNull()?.man?.lastName.orEmpty()
}

/**
 * Which name(s) to actually search, given gender — a man (or unspecified gender) always
 * searches only his own name. A woman with a resolved partner searches only his surname
 * (couple lessons are listed under it); without a partner she falls back to her own name
 * (solo lessons are listed under it). Centralized so the ViewModel (building "my
 * schedule") and the UI (deciding which column matched) never disagree.
 */
fun effectiveSearchNames(gender: Gender?, myTableName: String, partnerName: String): Pair<String, String> {
    if (gender != Gender.FEMALE) return myTableName to ""
    return if (partnerName.isNotBlank()) "" to partnerName else myTableName to ""
}

/**
 * "My" entries for a day: every note row with actual text (merged full-width rows apply
 * to the whole camp, e.g. meals or assembly notices — a note row OCR'd to blank has
 * nothing useful to show and is dropped) plus lesson rows whose block covers my chosen
 * group number (e.g. "STT 2 a 3" matches group 2 or 3) or that contain my resolved
 * table name (or, if I'm the woman in a couple, my partner's surname) anywhere in
 * [ScheduleEntry.Lesson.entries]. Matching by name/group number rather than by grid
 * position also makes this robust to OCR cells landing in the "wrong" column/row from
 * photo perspective distortion — the content is still found wherever it ended up.
 */
fun computeMySchedule(day: ScheduleDay, myTableName: String, myGroupNumber: Int?, partnerName: String = ""): List<ScheduleEntry> {
    val names = listOf(myTableName, partnerName).map { it.trim() }.filter { it.isNotEmpty() }
    return day.schedule.filter { entry ->
        when (entry) {
            is ScheduleEntry.Note -> !entry.text.isNullOrBlank()
            is ScheduleEntry.Lesson ->
                isGroupMatch(entry, myGroupNumber) ||
                    entry.entries.values.any { value -> value != null && names.any { cellMatchesName(value, it) } }
        }
    }
}

/**
 * A cell can name two people sharing a slot, joined with "-" or "," (e.g.
 * "Grulichová-Krbečková" is two girls, not one hyphenated surname; "Girl1, Girl2" the
 * same with a comma) — so a match checks the whole cell text first, then each
 * individually split piece. Each of those checks tries an exact match first, falling
 * back to a diacritic-insensitive one (see [foldDiacritics]) so a typed name missing an
 * accent — "Krizan"/"Křižan" for "Křížan" — still matches.
 */
private fun cellMatchesName(cellValue: String, target: String): Boolean {
    val trimmedTarget = target.trim()
    if (trimmedTarget.isEmpty()) return false
    val trimmedValue = cellValue.trim()
    if (namesMatch(trimmedValue, trimmedTarget)) return true
    return trimmedValue.split("-", ",").any { namesMatch(it.trim(), trimmedTarget) }
}

/**
 * True if [entry]'s block covers [myGroupNumber] — as opposed to it only being included
 * in "my schedule" because my name appears somewhere in its entries. A lesson can match
 * by name alone (an individual lesson slot) while its block names an unrelated group
 * that simply runs at the same time, so callers must not assume a non-null block means
 * a group match.
 */
fun isGroupMatch(entry: ScheduleEntry.Lesson, myGroupNumber: Int?): Boolean =
    myGroupNumber != null && entry.block != null && myGroupNumber in blockGroupNumbers(entry.block)

/** Every entry in [computeMySchedule] — lessons and notes alike — i.e. the reminder targets for the day. */
fun myReminderTargets(day: ScheduleDay, myTableName: String, myGroupNumber: Int?, partnerName: String = ""): List<ScheduleEntry> =
    computeMySchedule(day, myTableName, myGroupNumber, partnerName)

/**
 * The single column whose value matched my table name (or my partner's surname) in this
 * lesson, if any (as opposed to a match purely by group number) — used to show just
 * "time + name" for an individual lesson rather than every column's value.
 */
fun myMatchedColumn(entry: ScheduleEntry.Lesson, myTableName: String, partnerName: String = ""): String? {
    val targets = listOf(myTableName, partnerName).map { it.trim() }.filter { it.isNotEmpty() }
    if (targets.isEmpty()) return null
    return entry.entries.entries.firstOrNull { (_, value) -> value != null && targets.any { cellMatchesName(value, it) } }?.key
}
