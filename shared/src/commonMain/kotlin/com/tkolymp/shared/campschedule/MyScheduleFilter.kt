package com.tkolymp.shared.campschedule

import com.tkolymp.shared.people.PersonDetails

/**
 * The name that will appear in the photographed table for this person: for a couple,
 * the table always lists the man's surname regardless of which partner is viewing;
 * solo participants are listed by their own first name in individual-lesson columns.
 */
fun resolveMyTableName(personDetails: PersonDetails): String =
    personDetails.activeCouplesList.firstOrNull()?.man?.lastName?.takeIf { it.isNotBlank() }
        ?: personDetails.firstName.orEmpty()

/**
 * "My" entries for a day: every note row (merged full-width rows apply to the whole
 * camp, e.g. meals or assembly notices) plus lesson rows whose block covers my chosen
 * group number (e.g. "STT 2 a 3" matches group 2 or 3) or that contain my resolved
 * table name anywhere in [ScheduleEntry.Lesson.entries]. Matching by name/group number
 * rather than by grid position also makes this robust to OCR cells landing in the
 * "wrong" column/row from photo perspective distortion — the content is still found
 * wherever it ended up.
 */
fun computeMySchedule(day: ScheduleDay, myTableName: String, myGroupNumber: Int?): List<ScheduleEntry> =
    day.schedule.filter { entry ->
        when (entry) {
            is ScheduleEntry.Note -> true
            is ScheduleEntry.Lesson ->
                (myGroupNumber != null && entry.block != null && myGroupNumber in blockGroupNumbers(entry.block)) ||
                    entry.entries.values.any { it != null && it.trim().equals(myTableName.trim(), ignoreCase = true) }
        }
    }

/** The subset of [computeMySchedule] that are lessons, i.e. the reminder targets for the day. */
fun myLessonReminderTargets(day: ScheduleDay, myTableName: String, myGroupNumber: Int?): List<ScheduleEntry.Lesson> =
    computeMySchedule(day, myTableName, myGroupNumber).filterIsInstance<ScheduleEntry.Lesson>()
