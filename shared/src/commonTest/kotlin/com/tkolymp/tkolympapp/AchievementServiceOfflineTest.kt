package com.tkolymp.tkolympapp

import com.tkolymp.shared.achievements.AchievementContext
import com.tkolymp.shared.achievements.AchievementService
import com.tkolymp.shared.achievements.AchievementStorage
import com.tkolymp.shared.achievements.CampOccurrence
import com.tkolymp.shared.achievements.EarnedBadge
import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.event.AttendanceRepository
import com.tkolymp.shared.people.PeopleService
import com.tkolymp.shared.user.UserService
import com.tkolymp.tkolympapp.fakes.FakeCompetitionService
import com.tkolymp.tkolympapp.fakes.FakeEventService
import com.tkolymp.tkolympapp.fakes.FakeGraphQlClient
import com.tkolymp.tkolympapp.fakes.FakeOfflineDataStorage
import com.tkolymp.tkolympapp.fakes.FakeUserStorage
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AchievementServiceOfflineTest {
    private val earnedOn = LocalDate(2025, 3, 1)

    private fun service(storage: AchievementStorage, offline: Boolean) = AchievementService(
        eventService = FakeEventService(throwOnGroupedFetch = offline),
        peopleService = PeopleService(FakeGraphQlClient(), CacheService()),
        userService = UserService(FakeGraphQlClient(), FakeUserStorage()),
        competitionService = FakeCompetitionService(),
        attendanceRepository = AttendanceRepository(FakeGraphQlClient()),
        storage = storage,
    )

    @Test
    fun offlineEvaluationKeepsStoredBadgesAndDoesNotOverwriteThem() = runTest {
        val storage = AchievementStorage(FakeOfflineDataStorage())
        val stored = listOf(EarnedBadge("some_previously_earned_badge", earnedOn))
        storage.saveEarnedBadges(stored)

        val result = service(storage, offline = true).evaluateAndPersist()

        assertTrue(result.context.isIncomplete)
        assertTrue(result.earnedBadges.any { it.id == "some_previously_earned_badge" && it.earnedOn == earnedOn })
        assertTrue(result.newlyEarnedIds.isEmpty())
        assertEquals(stored, storage.loadEarnedBadges())
    }

    @Test
    fun onlineEvaluationStillKeepsPreviouslyEarnedBadgesAndIsComplete() = runTest {
        val storage = AchievementStorage(FakeOfflineDataStorage())
        storage.saveEarnedBadges(listOf(EarnedBadge("some_previously_earned_badge", earnedOn)))

        val result = service(storage, offline = false).evaluateAndPersist()

        assertTrue(!result.context.isIncomplete)
        assertTrue(result.earnedBadges.any { it.id == "some_previously_earned_badge" })
        assertTrue(storage.loadEarnedBadges().any { it.id == "some_previously_earned_badge" })
    }

    @Test
    fun offlineEvaluationFallsBackToLastStoredContext() = runTest {
        val storage = AchievementStorage(FakeOfflineDataStorage())
        val camp = CampOccurrence(7, "Camp", earnedOn, earnedOn, 2024, true)
        storage.saveContext(AchievementContext(listOf(camp), earnedOn, earnedOn, 3, 40))

        val result = service(storage, offline = true).evaluateAndPersist()

        assertEquals(listOf(camp), result.context.completedCamps)
        assertEquals(40, result.context.totalLessonsAttended)
        assertTrue(result.context.isIncomplete)
    }

    @Test
    fun refreshOfflineContextStoresSnapshotOnlyWhenComplete() = runTest {
        val storage = AchievementStorage(FakeOfflineDataStorage())
        service(storage, offline = true).refreshOfflineContext()
        assertEquals(null, storage.loadContext())
        service(storage, offline = false).refreshOfflineContext()
        assertTrue(storage.loadContext() != null)
        assertTrue(storage.loadEarnedBadges().isEmpty())
    }
}
