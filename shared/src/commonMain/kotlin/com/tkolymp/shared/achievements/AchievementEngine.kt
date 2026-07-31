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

        fun addCompetitions(id: String, count: Int) {
            if (context.competitionsCompleted >= count) earned += EarnedBadge(id, context.today)
        }
        addCompetitions(BadgeIds.COMPETITION_FIRST, 1)
        addCompetitions(BadgeIds.COMPETITION_10, 10)
        addCompetitions(BadgeIds.COMPETITION_25, 25)

        if (context.competitionFinalsReached >= 1) {
            earned += EarnedBadge(BadgeIds.COMPETITION_FINAL_FIRST, context.today)
        }
        context.bestRanking?.let { ranking ->
            if (ranking <= 3) earned += EarnedBadge(BadgeIds.COMPETITION_PODIUM, context.today)
            if (ranking == 1) earned += EarnedBadge(BadgeIds.COMPETITION_CHAMPION, context.today)
        }
        if (context.distinctDanceStyles >= 2) {
            earned += EarnedBadge(BadgeIds.COMPETITION_VERSATILE, context.today)
        }

        fun addPartnership(id: String, seasons: Int) {
            if (context.longestPartnershipSeasons >= seasons) earned += EarnedBadge(id, context.today)
        }
        addPartnership(BadgeIds.PARTNERSHIP_3, 3)
        addPartnership(BadgeIds.PARTNERSHIP_5, 5)

        fun addTrainers(id: String, count: Int) {
            if (context.distinctTrainers >= count) earned += EarnedBadge(id, context.today)
        }
        addTrainers(BadgeIds.TRAINERS_3, 3)
        addTrainers(BadgeIds.TRAINERS_5, 5)
        addTrainers(BadgeIds.TRAINERS_10, 10)

        if (context.distinctEventTypes >= 3) {
            earned += EarnedBadge(BadgeIds.TYPES_EXPLORER, context.today)
        }

        if (context.earlyBirdCount >= 10) earned += EarnedBadge(BadgeIds.EARLY_BIRD, context.today)
        if (context.nightOwlCount >= 10) earned += EarnedBadge(BadgeIds.NIGHT_OWL, context.today)
        if (context.weekendLessonsCount >= 20) earned += EarnedBadge(BadgeIds.WEEKEND_WARRIOR, context.today)
        if (context.returnedAfterBreak) earned += EarnedBadge(BadgeIds.COMEBACK, context.today)

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
        BadgeIds.COMPETITION_FIRST -> context.competitionsCompleted to 1
        BadgeIds.COMPETITION_10 -> context.competitionsCompleted to 10
        BadgeIds.COMPETITION_25 -> context.competitionsCompleted to 25
        BadgeIds.COMPETITION_VERSATILE -> context.distinctDanceStyles to 2
        BadgeIds.PARTNERSHIP_3 -> context.longestPartnershipSeasons to 3
        BadgeIds.PARTNERSHIP_5 -> context.longestPartnershipSeasons to 5
        BadgeIds.TRAINERS_3 -> context.distinctTrainers to 3
        BadgeIds.TRAINERS_5 -> context.distinctTrainers to 5
        BadgeIds.TRAINERS_10 -> context.distinctTrainers to 10
        BadgeIds.TYPES_EXPLORER -> context.distinctEventTypes to 3
        BadgeIds.EARLY_BIRD -> context.earlyBirdCount to 10
        BadgeIds.NIGHT_OWL -> context.nightOwlCount to 10
        BadgeIds.WEEKEND_WARRIOR -> context.weekendLessonsCount to 20
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
