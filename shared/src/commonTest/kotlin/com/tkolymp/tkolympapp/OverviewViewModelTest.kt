package com.tkolymp.tkolympapp

import com.tkolymp.shared.announcements.Announcement
import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.event.Event
import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.people.PeopleService
import com.tkolymp.shared.user.UserService
import com.tkolymp.shared.viewmodels.AppError
import com.tkolymp.shared.viewmodels.OverviewViewModel
import com.tkolymp.tkolympapp.fakes.FakeAnnouncementService
import com.tkolymp.tkolympapp.fakes.FakeEventService
import com.tkolymp.tkolympapp.fakes.FakeGraphQlClient
import com.tkolymp.tkolympapp.fakes.FakeUserStorage
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

private fun makeEventInstance(id: Long, type: String): EventInstance {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    return EventInstance(
        id = id,
        isCancelled = false,
        since = "${today}T10:00:00Z",
        until = "${today}T11:00:00Z",
        updatedAt = null,
        event = Event(
            id = id,
            name = "Event $id",
            description = null,
            type = type,
            locationText = null,
            isRegistrationOpen = true,
            isVisible = true,
            isPublic = false,
            eventTrainersList = if (type == "lesson") listOf("Trainer A") else emptyList(),
            eventTargetCohortsList = emptyList(),
            eventRegistrationsList = emptyList(),
            location = null
        )
    )
}

private fun makeOverviewVm(
    eventService: com.tkolymp.shared.event.IEventService = FakeEventService(),
    announcementService: com.tkolymp.shared.announcements.IAnnouncementService = FakeAnnouncementService()
) = OverviewViewModel(
    eventService = eventService,
    announcementService = announcementService,
    userService = UserService(FakeGraphQlClient(), FakeUserStorage()),
    peopleService = PeopleService(FakeGraphQlClient(), CacheService()),
    cache = CacheService()
)

class OverviewViewModelTest {

    @Test
    fun `initial state is clean`() = runTest {
        val vm = makeOverviewVm()
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.upcomingEvents.isEmpty())
        assertTrue(vm.state.value.recentAnnouncements.isEmpty())
    }

    @Test
    fun `loadOverview with network error gracefully degrades to empty state`() = runTest {
        // OverviewViewModel intentionally swallows fetch errors (offline-resilience design):
        // it falls back to empty state rather than surfacing an AppError.
        val vm = makeOverviewVm(eventService = FakeEventService(throwOnGroupedFetch = true))
        vm.loadOverview()
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.error)
        assertTrue(vm.state.value.upcomingEvents.isEmpty())
    }

    @Test
    fun `loadOverview populates upcomingEvents`() = runTest {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        val inst = makeEventInstance(1L, "group")
        val vm = makeOverviewVm(
            eventService = FakeEventService(groupedByDay = mapOf(today to listOf(inst)))
        )
        vm.loadOverview()
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.error)
        assertEquals(1, vm.state.value.upcomingEvents.size)
    }

    @Test
    fun `CAMP events appear in campsMapByDay`() = runTest {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        val camp = makeEventInstance(2L, "CAMP")
        val vm = makeOverviewVm(
            eventService = FakeEventService(groupedByDay = mapOf(today to listOf(camp)))
        )
        vm.loadOverview()
        assertTrue(vm.state.value.campsMapByDay.isNotEmpty())
    }

    @Test
    fun `lesson events with trainer appear in trainingLessonsByTrainer`() = runTest {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        val lesson = makeEventInstance(3L, "lesson")
        val vm = makeOverviewVm(
            eventService = FakeEventService(groupedByDay = mapOf(today to listOf(lesson)))
        )
        vm.loadOverview()
        assertTrue(vm.state.value.trainingLessonsByTrainer.isNotEmpty())
    }

    @Test
    fun `announcements from service populate recentAnnouncements`() = runTest {
        val ann = Announcement(id = "1", title = "Hello world", isSticky = false, isVisible = true)
        val vm = makeOverviewVm(
            announcementService = FakeAnnouncementService(announcements = listOf(ann))
        )
        vm.loadOverview()
        assertEquals(1, vm.state.value.recentAnnouncements.size)
        assertEquals("Hello world", vm.state.value.recentAnnouncements.first().title)
    }

    @Test
    fun `non-camp non-lesson event appears in trainingOtherEvents`() = runTest {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        val other = makeEventInstance(4L, "group")
        val vm = makeOverviewVm(
            eventService = FakeEventService(groupedByDay = mapOf(today to listOf(other)))
        )
        vm.loadOverview()
        assertTrue(vm.state.value.trainingOtherEvents.isNotEmpty())
    }
}
