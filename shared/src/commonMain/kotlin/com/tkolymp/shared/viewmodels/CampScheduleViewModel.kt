package com.tkolymp.shared.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.campschedule.CampScheduleReminderService
import com.tkolymp.shared.campschedule.CampScheduleService
import com.tkolymp.shared.campschedule.ScheduleDay
import com.tkolymp.shared.campschedule.ScheduleEntry
import com.tkolymp.shared.campschedule.availableGroupNumbers
import com.tkolymp.shared.campschedule.campDates
import com.tkolymp.shared.campschedule.computeMySchedule
import com.tkolymp.shared.campschedule.dayIndex
import com.tkolymp.shared.campschedule.myLessonReminderTargets
import com.tkolymp.shared.campschedule.resolveMyTableName
import com.tkolymp.shared.user.UserService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.JsonObject

@Immutable
data class CampScheduleState(
    val eventId: Long? = null,
    val campDates: List<LocalDate> = emptyList(),
    val storedDayIndexes: Set<Int> = emptySet(),
    val selectedDate: LocalDate? = null,
    val day: ScheduleDay? = null,
    val availableGroupNumbers: List<Int> = emptyList(),
    val myGroupNumber: Int? = null,
    val myTableName: String = "",
    val mySchedule: List<ScheduleEntry> = emptyList(),
    val reminderMinutes: Int = 30,
    override val isLoading: Boolean = false,
    override val error: AppError? = null
) : ViewModelState

class CampScheduleViewModel(
    private val campScheduleService: CampScheduleService = ServiceLocator.campScheduleService,
    private val campScheduleReminderService: CampScheduleReminderService = ServiceLocator.campScheduleReminderService,
    private val userService: UserService = ServiceLocator.userService
) : ViewModel() {
    private val _state = MutableStateFlow(CampScheduleState())
    val state: StateFlow<CampScheduleState> = _state.asStateFlow()

    suspend fun load(eventId: Long, instances: List<JsonObject>) {
        _state.value = _state.value.copy(isLoading = true, error = null, eventId = eventId)
        try {
            val dates = campDates(instances)
            val stored = campScheduleService.listStoredDayIndexes(eventId)
            val today = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())
            val initialDate = dates.firstOrNull { it == today } ?: dates.firstOrNull()
            val myTableName = campScheduleService.loadMyNameOverride(eventId) ?: resolveMyTableNameCached()
            val reminderMinutes = campScheduleService.getReminderMinutes(eventId)
            _state.value = _state.value.copy(
                campDates = dates,
                storedDayIndexes = stored,
                myTableName = myTableName,
                reminderMinutes = reminderMinutes
            )
            if (initialDate != null) selectDate(initialDate) else _state.value = _state.value.copy(isLoading = false)
        } catch (e: CancellationException) {
            throw e
        } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = AppError.generic(ex.message))
        }
    }

    suspend fun selectDate(date: LocalDate) {
        val eventId = _state.value.eventId ?: return
        val campStart = _state.value.campDates.firstOrNull() ?: return
        val idx = dayIndex(campStart, date)
        _state.value = _state.value.copy(isLoading = true, selectedDate = date)
        try {
            val day = campScheduleService.loadDay(eventId, idx)
            val myGroupNumber = campScheduleService.loadGroupNumber(eventId, idx)
            val numbers = day?.let { availableGroupNumbers(it) } ?: emptyList()
            val mySchedule = day?.let { computeMySchedule(it, _state.value.myTableName, myGroupNumber) } ?: emptyList()
            _state.value = _state.value.copy(
                day = day,
                availableGroupNumbers = numbers,
                myGroupNumber = myGroupNumber,
                mySchedule = mySchedule,
                isLoading = false
            )
        } catch (e: CancellationException) {
            throw e
        } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = AppError.generic(ex.message))
        }
    }

    /** Called once the Android capture pipeline has produced a [ScheduleDay] for the selected date. */
    suspend fun saveDay(day: ScheduleDay) {
        val eventId = _state.value.eventId ?: return
        val date = _state.value.selectedDate ?: return
        val campStart = _state.value.campDates.firstOrNull() ?: return
        val idx = dayIndex(campStart, date)
        campScheduleService.saveDay(eventId, idx, day)
        _state.value = _state.value.copy(storedDayIndexes = _state.value.storedDayIndexes + idx)
        selectDate(date)
        rescheduleReminders()
    }

    /** Manual override for "my name" (e.g. for debugging/testing against a name the account doesn't resolve to). */
    suspend fun setMyTableName(name: String) {
        val eventId = _state.value.eventId ?: return
        campScheduleService.saveMyNameOverride(eventId, name)
        val mySchedule = _state.value.day?.let { computeMySchedule(it, name, _state.value.myGroupNumber) } ?: emptyList()
        _state.value = _state.value.copy(myTableName = name, mySchedule = mySchedule)
        rescheduleReminders()
    }

    suspend fun setGroupNumber(number: Int) {
        val eventId = _state.value.eventId ?: return
        val date = _state.value.selectedDate ?: return
        val campStart = _state.value.campDates.firstOrNull() ?: return
        val idx = dayIndex(campStart, date)
        campScheduleService.saveGroupNumber(eventId, idx, number)
        val mySchedule = _state.value.day?.let { computeMySchedule(it, _state.value.myTableName, number) } ?: emptyList()
        _state.value = _state.value.copy(myGroupNumber = number, mySchedule = mySchedule)
        rescheduleReminders()
    }

    /** One shared reminder-minutes value applies to every stored day of this camp. */
    suspend fun setReminderMinutes(minutes: Int) {
        val eventId = _state.value.eventId ?: return
        campScheduleService.setReminderMinutes(eventId, minutes)
        _state.value = _state.value.copy(reminderMinutes = minutes)
        val campStart = _state.value.campDates.firstOrNull() ?: return
        campScheduleService.listStoredDayIndexes(eventId).forEach { idx ->
            val date = campStart.plus(DatePeriod(days = idx))
            val day = campScheduleService.loadDay(eventId, idx) ?: return@forEach
            val myGroupNumber = campScheduleService.loadGroupNumber(eventId, idx)
            val myLessons = myLessonReminderTargets(day, _state.value.myTableName, myGroupNumber)
            campScheduleReminderService.rescheduleForDay(eventId, idx, date, myLessons, minutes)
        }
    }

    private suspend fun rescheduleReminders() {
        val eventId = _state.value.eventId ?: return
        val date = _state.value.selectedDate ?: return
        val campStart = _state.value.campDates.firstOrNull() ?: return
        val idx = dayIndex(campStart, date)
        val day = _state.value.day ?: return
        val myLessons = myLessonReminderTargets(day, _state.value.myTableName, _state.value.myGroupNumber)
        campScheduleReminderService.rescheduleForDay(eventId, idx, date, myLessons, _state.value.reminderMinutes)
    }

    private suspend fun resolveMyTableNameCached(): String {
        val pid = try { userService.getCachedPersonId() } catch (e: CancellationException) { throw e } catch (_: Exception) { null } ?: return ""
        var person = try { userService.getCachedPersonDetails() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        if (person == null) {
            try { userService.fetchAndStorePersonDetails(pid) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
            person = try { userService.getCachedPersonDetails() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        }
        return person?.let { resolveMyTableName(it) } ?: ""
    }
}
