package com.tkolymp.shared.language

data class OverviewStrings(
    val upcomingTrainings: String,
    val upcomingCamps: String,
    val fromTheBoard: String,
    val more: String,
    val browseOthers: String,
    val nextTraining: String,
    /** Contains one `%d` for the minutes. */
    val inMinutes: String,
    /** Contains two `%d`: hours, then minutes. */
    val inHoursMinutes: String,
    /** Contains one `%d` for the count. */
    val freeLessonsToday: String,
    val freeLesson: String,
    val pay: String,
    /** Unit shown under the countdown number, e.g. "days". */
    val daysUnit: String,
    /** Contains one `%d` for the day count. */
    val inDays: String,
)
