package com.tkolymp.tkolympapp

import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.event.Event
import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.user.UserService
import com.tkolymp.shared.viewmodels.AppError
import com.tkolymp.shared.viewmodels.CalendarViewViewModel
import com.tkolymp.tkolympapp.fakes.FakeEventService
import com.tkolymp.tkolympapp.fakes.FakeGraphQlClient
import com.tkolymp.tkolympapp.fakes.FakeUserStorage
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

private fun todayEventInstance(id: Long, type: String = "group"): EventInstance {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    return EventInstance(
        id = id,
        isCancelled = false,
        since = "${today}T10:00:00Z",
        until = "${today}T11:00:00Z",
        updatedAt = null,
        event = Event(
            id = id,
            name = "Test Event $id",
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

private fun makeCalVm(
    eventService: com.tkolymp.shared.event.IEventService = FakeEventService()
) = CalendarViewViewModel(
    eventService = eventService,
    userService = UserService(FakeGraphQlClient(), FakeUserStorage()),
    cache = CacheService()
)

class CalendarViewModelTest {

    @Test
    fun `initial state is clean`() = runTest {
        val vm = makeCalVm()
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.events.isEmpty())
    }

    @Test
    fun `loadEvents network failure sets AppError`() = runTest {
        val vm = makeCalVm(eventService = FakeEventService(throwOnGroupedFetch = true))
        vm.loadEvents()
        assertFalse(vm.state.value.isLoading)
        assertIs<AppError>(vm.state.value.error)
    }

    @Test
    fun `clearError after failure resets to null`() = runTest {
        val vm = makeCalVm(eventService = FakeEventService(throwOnGroupedFetch = true))
        vm.loadEvents()
        assertIs<AppError>(vm.state.value.error)
        vm.clearError()
        assertNull(vm.state.value.error)
    }

    @Test
    fun `loadEvents with today events populates timeline`() = runTest {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        val inst = todayEventInstance(1L)
        val vm = makeCalVm(
            eventService = FakeEventService(groupedByDay = mapOf(today to listOf(inst)))
        )
        vm.loadEvents()
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.error)
        assertTrue(vm.state.value.events.isNotEmpty())
    }

    @Test
    fun `loadEvents empty service result keeps events empty`() = runTest {
        val vm = makeCalVm(eventService = FakeEventService(groupedByDay = emptyMap()))
        vm.loadEvents()
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.error)
        assertTrue(vm.state.value.events.isEmpty())
    }
}
