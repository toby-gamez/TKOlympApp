package com.tkolymp.tkolympapp

import com.tkolymp.shared.notification.NotificationService
import com.tkolymp.shared.user.UserService
import com.tkolymp.shared.viewmodels.AppError
import com.tkolymp.shared.viewmodels.EventSideEffect
import com.tkolymp.shared.viewmodels.EventViewModel
import com.tkolymp.tkolympapp.fakes.FakeCalendarPreferenceStorage
import com.tkolymp.tkolympapp.fakes.FakeEventService
import com.tkolymp.tkolympapp.fakes.FakeGraphQlClient
import com.tkolymp.tkolympapp.fakes.FakeNotificationScheduler
import com.tkolymp.tkolympapp.fakes.FakeNotificationStorage
import com.tkolymp.tkolympapp.fakes.FakeSystemCalendarService
import com.tkolymp.tkolympapp.fakes.FakeUserStorage
import com.tkolymp.tkolympapp.fakes.minimalEventJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun makeVm(
    eventService: com.tkolymp.shared.event.IEventService = FakeEventService(minimalEventJson()),
    calResult: Boolean = false
) = EventViewModel(
    eventService = eventService,
    userService = UserService(FakeGraphQlClient(), FakeUserStorage()),
    calendarStorage = FakeCalendarPreferenceStorage(),
    systemCalendarService = FakeSystemCalendarService(calResult),
    notificationService = NotificationService(
        storage = FakeNotificationStorage(),
        scheduler = FakeNotificationScheduler(),
        eventService = eventService
    )
)

@OptIn(ExperimentalCoroutinesApi::class)
class EventViewModelTest {

    @Test
    fun `initial state is clean`() = runTest {
        val vm = makeVm()
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `loadEvent success sets eventName and clears loading`() = runTest {
        val vm = makeVm()
        vm.loadEvent(1L)
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.error)
        assertEquals("Test Event", vm.state.value.eventName)
    }

    @Test
    fun `loadEvent network failure sets AppError`() = runTest {
        val vm = makeVm(eventService = FakeEventService(throwOnFetch = true))
        vm.loadEvent(99L)
        assertFalse(vm.state.value.isLoading)
        assertIs<AppError>(vm.state.value.error)
    }

    @Test
    fun `loadEvent null result sets NotFound error`() = runTest {
        val vm = makeVm(eventService = FakeEventService(eventById = null))
        vm.loadEvent(404L)
        assertIs<AppError.NotFound>(vm.state.value.error)
    }

    @Test
    fun `addToCalendar emits CalendarResult side effect`() = runTest {
        val vm = makeVm(calResult = true)
        vm.loadEvent(1L)
        val effects = mutableListOf<EventSideEffect>()
        val job = launch { vm.sideEffect.collect { effects.add(it) } }
        vm.addToCalendar(1L)
        advanceUntilIdle()
        job.cancel()
        val calEffect = effects.filterIsInstance<EventSideEffect.CalendarResult>()
        assertTrue(calEffect.isNotEmpty())
        assertTrue(calEffect.first().success)
    }

    @Test
    fun `addToCalendar false emits failure side effect`() = runTest {
        val vm = makeVm(calResult = false)
        vm.loadEvent(1L)
        val effects = mutableListOf<EventSideEffect>()
        val job = launch { vm.sideEffect.collect { effects.add(it) } }
        vm.addToCalendar(1L)
        advanceUntilIdle()
        job.cancel()
        val calEffect = effects.filterIsInstance<EventSideEffect.CalendarResult>()
        assertTrue(calEffect.isNotEmpty())
        assertFalse(calEffect.first().success)
    }

    @Test
    fun `isAddedToCalendar set to true after successful addToCalendar`() = runTest {
        val vm = makeVm(calResult = true)
        vm.loadEvent(1L)
        vm.addToCalendar(1L)
        assertTrue(vm.state.value.isAddedToCalendar)
    }
}
