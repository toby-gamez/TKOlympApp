package com.tkolymp.tkolympapp

import com.tkolymp.shared.achievements.AchievementContext
import com.tkolymp.shared.achievements.AchievementEngine
import com.tkolymp.shared.achievements.BadgeIds
import com.tkolymp.shared.achievements.CampOccurrence
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val TODAY = LocalDate(2026, 6, 1)

private fun camp(
    eventId: Long,
    start: LocalDate,
    end: LocalDate = start,
    seasonStartYear: Int = if (start.month.number >= 9) start.year else start.year - 1,
    attendedAllDays: Boolean = true,
) = CampOccurrence(eventId, "Camp $eventId", start, end, seasonStartYear, attendedAllDays)

private fun emptyContext(
    completedCamps: List<CampOccurrence> = emptyList(),
    memberSinceDate: LocalDate? = null,
    longestStreakWeeks: Int = 0,
    totalLessonsAttended: Int = 0,
) = AchievementContext(completedCamps, memberSinceDate, TODAY, longestStreakWeeks, totalLessonsAttended)

class AchievementEngineTest {

    @Test
    fun `no data earns nothing`() {
        val earned = AchievementEngine.evaluate(emptyContext())
        assertTrue(earned.isEmpty())
    }

    @Test
    fun `first camp earns only the first-camp badge`() {
        val context = emptyContext(completedCamps = listOf(camp(1, LocalDate(2025, 7, 1), attendedAllDays = false)))
        val ids = AchievementEngine.evaluate(context).map { it.id }
        assertEquals(listOf(BadgeIds.CAMP_FIRST), ids)
    }

    @Test
    fun `exactly 3 camps earns first and 3-camp badges but not 5`() {
        val camps = (1..3).map { camp(it.toLong(), LocalDate(2025, 7, it)) }
        val ids = AchievementEngine.evaluate(emptyContext(completedCamps = camps)).map { it.id }.toSet()
        assertTrue(BadgeIds.CAMP_FIRST in ids)
        assertTrue(BadgeIds.CAMP_3 in ids)
        assertFalse(BadgeIds.CAMP_5 in ids)
    }

    @Test
    fun `camp milestone earned date is the date of the milestone-th camp`() {
        val camps = listOf(
            camp(1, LocalDate(2023, 1, 1)),
            camp(2, LocalDate(2024, 1, 1)),
            camp(3, LocalDate(2025, 1, 1)),
        )
        val earned = AchievementEngine.evaluate(emptyContext(completedCamps = camps))
        val camp3 = earned.first { it.id == BadgeIds.CAMP_3 }
        assertEquals(LocalDate(2025, 1, 1), camp3.earnedOn)
    }

    @Test
    fun `perfect attendance badge requires at least one fully-attended camp`() {
        val partial = camp(1, LocalDate(2025, 1, 1), attendedAllDays = false)
        val full = camp(2, LocalDate(2025, 2, 1), attendedAllDays = true)

        val withoutPerfect = AchievementEngine.evaluate(emptyContext(completedCamps = listOf(partial))).map { it.id }
        assertFalse(BadgeIds.CAMP_PERFECT in withoutPerfect)

        val withPerfect = AchievementEngine.evaluate(emptyContext(completedCamps = listOf(partial, full))).map { it.id }
        assertTrue(BadgeIds.CAMP_PERFECT in withPerfect)
    }

    @Test
    fun `multi-season badge requires camps in at least 3 distinct seasons`() {
        val twoSeasons = listOf(
            camp(1, LocalDate(2023, 7, 1), seasonStartYear = 2023),
            camp(2, LocalDate(2024, 7, 1), seasonStartYear = 2024),
        )
        assertFalse(BadgeIds.CAMP_MULTI_SEASON in AchievementEngine.evaluate(emptyContext(completedCamps = twoSeasons)).map { it.id })

        val threeSeasons = twoSeasons + camp(3, LocalDate(2025, 7, 1), seasonStartYear = 2025)
        assertTrue(BadgeIds.CAMP_MULTI_SEASON in AchievementEngine.evaluate(emptyContext(completedCamps = threeSeasons)).map { it.id })
    }

    @Test
    fun `repeated camps in the same season do not count as distinct seasons`() {
        val sameSeasonTwice = listOf(
            camp(1, LocalDate(2025, 7, 1), seasonStartYear = 2024),
            camp(2, LocalDate(2025, 8, 1), seasonStartYear = 2024),
            camp(3, LocalDate(2025, 8, 15), seasonStartYear = 2024),
        )
        assertFalse(BadgeIds.CAMP_MULTI_SEASON in AchievementEngine.evaluate(emptyContext(completedCamps = sameSeasonTwice)).map { it.id })
    }

    @Test
    fun `membership badges earned once the anniversary has passed`() {
        val since = LocalDate(2021, 6, 1) // exactly 5 years before TODAY
        val ids = AchievementEngine.evaluate(emptyContext(memberSinceDate = since)).map { it.id }.toSet()
        assertTrue(BadgeIds.MEMBERSHIP_1 in ids)
        assertTrue(BadgeIds.MEMBERSHIP_2 in ids)
        assertTrue(BadgeIds.MEMBERSHIP_3 in ids)
        assertTrue(BadgeIds.MEMBERSHIP_5 in ids)
        assertFalse(BadgeIds.MEMBERSHIP_7 in ids)
        assertFalse(BadgeIds.MEMBERSHIP_10 in ids)
        assertFalse(BadgeIds.MEMBERSHIP_15 in ids)
    }

    @Test
    fun `membership badge not earned before the anniversary date`() {
        val since = LocalDate(2025, 12, 1) // 1-year anniversary is after TODAY
        val ids = AchievementEngine.evaluate(emptyContext(memberSinceDate = since)).map { it.id }
        assertFalse(BadgeIds.MEMBERSHIP_1 in ids)
    }

    @Test
    fun `long-tenure membership badges earned for veteran members`() {
        val since = LocalDate(2011, 6, 1) // 15 years before TODAY
        val ids = AchievementEngine.evaluate(emptyContext(memberSinceDate = since)).map { it.id }.toSet()
        assertTrue(BadgeIds.MEMBERSHIP_7 in ids)
        assertTrue(BadgeIds.MEMBERSHIP_10 in ids)
        assertTrue(BadgeIds.MEMBERSHIP_15 in ids)
    }

    @Test
    fun `streak badges respect thresholds`() {
        val ids = AchievementEngine.evaluate(emptyContext(longestStreakWeeks = 8)).map { it.id }.toSet()
        assertTrue(BadgeIds.STREAK_4 in ids)
        assertTrue(BadgeIds.STREAK_8 in ids)
        assertFalse(BadgeIds.STREAK_16 in ids)
    }

    @Test
    fun `lesson milestone badges respect thresholds`() {
        val ids = AchievementEngine.evaluate(emptyContext(totalLessonsAttended = 100)).map { it.id }.toSet()
        assertTrue(BadgeIds.LESSONS_50 in ids)
        assertTrue(BadgeIds.LESSONS_100 in ids)
        assertFalse(BadgeIds.LESSONS_250 in ids)
    }

    @Test
    fun `progressFor reports current versus target for counted badges`() {
        val context = emptyContext(completedCamps = listOf(camp(1, LocalDate(2025, 1, 1))), totalLessonsAttended = 12)
        assertEquals(1 to 3, AchievementEngine.progressFor(BadgeIds.CAMP_3, context))
        assertEquals(12 to 50, AchievementEngine.progressFor(BadgeIds.LESSONS_50, context))
    }

    @Test
    fun `progressFor returns null for boolean badges`() {
        assertEquals(null, AchievementEngine.progressFor(BadgeIds.CAMP_PERFECT, emptyContext()))
        assertEquals(null, AchievementEngine.progressFor(BadgeIds.MEMBERSHIP_1, emptyContext()))
    }
}
