package com.tkolymp.shared.achievements

import kotlinx.datetime.LocalDate

/**
 * A single soustředění (camp) the user attended at least one day of and that has
 * already ended. "Completed" is approximated as: camp end date is in the past AND
 * at least one of its daily instances has attendance status ATTENDED — the API has
 * no explicit "camp completed" flag.
 */
data class CampOccurrence(
    val eventId: Long,
    val name: String?,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val seasonStartYear: Int,
    val attendedAllDays: Boolean,
)

/** Snapshot of the signals [AchievementEngine] evaluates badges against. */
data class AchievementContext(
    val completedCamps: List<CampOccurrence>,
    val memberSinceDate: LocalDate?,
    val today: LocalDate,
    val longestStreakWeeks: Int,
    val totalLessonsAttended: Int,
)
