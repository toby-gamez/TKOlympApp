package com.tkolymp.shared.achievements

import kotlinx.datetime.LocalDate

/** A badge the user has earned, with the date it was first earned. */
data class EarnedBadge(val id: String, val earnedOn: LocalDate)

/** Pure, deterministic badge evaluation. No I/O — all inputs come from [AchievementContext]. */
object AchievementEngine {

    fun evaluate(context: AchievementContext): List<EarnedBadge> {
        val earned = mutableListOf<EarnedBadge>()
        val camps = context.completedCamps.sortedBy { it.startDate }

        fun addCampMilestone(id: String, count: Int) {
            if (camps.size >= count) earned += EarnedBadge(id, camps[count - 1].startDate)
        }
        addCampMilestone(BadgeIds.CAMP_FIRST, 1)
        addCampMilestone(BadgeIds.CAMP_3, 3)
        addCampMilestone(BadgeIds.CAMP_5, 5)
        addCampMilestone(BadgeIds.CAMP_10, 10)
        addCampMilestone(BadgeIds.CAMP_20, 20)

        camps.firstOrNull { it.attendedAllDays }?.let { perfect ->
            earned += EarnedBadge(BadgeIds.CAMP_PERFECT, perfect.startDate)
        }

        val seasonsSeen = mutableSetOf<Int>()
        val thirdSeasonCamp = camps.firstOrNull { seasonsSeen.add(it.seasonStartYear) && seasonsSeen.size >= 3 }
        if (thirdSeasonCamp != null) {
            earned += EarnedBadge(BadgeIds.CAMP_MULTI_SEASON, thirdSeasonCamp.startDate)
        }

        context.memberSinceDate?.let { since ->
            fun addMembership(id: String, years: Int) {
                val anniversary = anniversaryOf(since, years)
                if (!anniversary.isAfter(context.today)) earned += EarnedBadge(id, anniversary)
            }
            addMembership(BadgeIds.MEMBERSHIP_1, 1)
            addMembership(BadgeIds.MEMBERSHIP_2, 2)
            addMembership(BadgeIds.MEMBERSHIP_3, 3)
            addMembership(BadgeIds.MEMBERSHIP_5, 5)
            addMembership(BadgeIds.MEMBERSHIP_7, 7)
            addMembership(BadgeIds.MEMBERSHIP_10, 10)
            addMembership(BadgeIds.MEMBERSHIP_15, 15)
        }

        fun addStreak(id: String, weeks: Int) {
            if (context.longestStreakWeeks >= weeks) earned += EarnedBadge(id, context.today)
        }
        addStreak(BadgeIds.STREAK_4, 4)
        addStreak(BadgeIds.STREAK_8, 8)
        addStreak(BadgeIds.STREAK_16, 16)

        fun addLessons(id: String, count: Int) {
            if (context.totalLessonsAttended >= count) earned += EarnedBadge(id, context.today)
        }
        addLessons(BadgeIds.LESSONS_50, 50)
        addLessons(BadgeIds.LESSONS_100, 100)
        addLessons(BadgeIds.LESSONS_250, 250)

        return earned
    }

    /** Current progress (earned/target) for badges that have a meaningful numeric counter; null for boolean badges. */
    fun progressFor(id: String, context: AchievementContext): Pair<Int, Int>? = when (id) {
        BadgeIds.CAMP_FIRST -> context.completedCamps.size to 1
        BadgeIds.CAMP_3 -> context.completedCamps.size to 3
        BadgeIds.CAMP_5 -> context.completedCamps.size to 5
        BadgeIds.CAMP_10 -> context.completedCamps.size to 10
        BadgeIds.CAMP_20 -> context.completedCamps.size to 20
        BadgeIds.CAMP_MULTI_SEASON -> context.completedCamps.map { it.seasonStartYear }.distinct().size to 3
        BadgeIds.STREAK_4 -> context.longestStreakWeeks to 4
        BadgeIds.STREAK_8 -> context.longestStreakWeeks to 8
        BadgeIds.STREAK_16 -> context.longestStreakWeeks to 16
        BadgeIds.LESSONS_50 -> context.totalLessonsAttended to 50
        BadgeIds.LESSONS_100 -> context.totalLessonsAttended to 100
        BadgeIds.LESSONS_250 -> context.totalLessonsAttended to 250
        else -> null
    }
}

private fun LocalDate.isAfter(other: LocalDate): Boolean = this > other

/** [since] shifted forward by [years], clamped to the last valid day of the target month (handles Feb 29). */
private fun anniversaryOf(since: LocalDate, years: Int): LocalDate {
    val targetYear = since.year + years
    var day = since.dayOfMonth
    while (day > 0) {
        val candidate = runCatching { LocalDate(targetYear, since.month, day) }.getOrNull()
        if (candidate != null) return candidate
        day--
    }
    return LocalDate(targetYear, since.month, 1)
}
