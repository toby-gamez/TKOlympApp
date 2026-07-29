package com.tkolymp.shared.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.campschedule.CampScheduleReminderService
import com.tkolymp.shared.campschedule.CampScheduleService
import com.tkolymp.shared.campschedule.Gender
import com.tkolymp.shared.campschedule.ScheduleDay
import com.tkolymp.shared.campschedule.ScheduleEntry
import com.tkolymp.shared.campschedule.availableGroupNumbers
import com.tkolymp.shared.campschedule.campDates
import com.tkolymp.shared.campschedule.computeMySchedule
import com.tkolymp.shared.campschedule.dayIndex
import com.tkolymp.shared.campschedule.effectiveSearchNames
import com.tkolymp.shared.campschedule.myReminderTargets
import com.tkolymp.shared.campschedule.resolveMyTableName
import com.tkolymp.shared.campschedule.resolvePartnerName
import com.tkolymp.shared.people.PersonDetails
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
    val eventName: String = "",
    val campDates: List<LocalDate> = emptyList(),
    val storedDayIndexes: Set<Int> = emptySet(),
    val selectedDate: LocalDate? = null,
    val day: ScheduleDay? = null,
    val photoBytes: ByteArray? = null,
    val availableGroupNumbers: List<Int> = emptyList(),
    val myGroupNumber: Int? = null,
    val myTableName: String = "",
    val myGender: Gender? = null,
    val myPartnerName: String = "",
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

    suspend fun load(eventId: Long, instances: List<JsonObject>, eventName: String = "") {
        _state.value = _state.value.copy(isLoading = true, error = null, eventId = eventId, eventName = eventName)
        try {
            val dates = campDates(instances)
            val stored = campScheduleService.listStoredDayIndexes(eventId)
            val today = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())
            val initialDate = dates.firstOrNull { it == today } ?: dates.firstOrNull()
            val person = cachedPersonDetails()
            val myGender = accountGender(person?.gender)
            val myTableName = person?.let { resolveMyTableName(it, myGender) }.orEmpty()
            val myPartnerName = person?.let { resolvePartnerName(it, myGender) }.orEmpty()
            val reminderMinutes = campScheduleService.getReminderMinutes(eventId)
            val myGroupNumber = campScheduleService.loadGroupNumber(eventId)
            val numbers = allAvailableGroupNumbers(eventId, stored)
            _state.value = _state.value.copy(
                campDates = dates,
                storedDayIndexes = stored,
                myTableName = myTableName,
                myGender = myGender,
                myPartnerName = myPartnerName,
                reminderMinutes = reminderMinutes,
                myGroupNumber = myGroupNumber,
                availableGroupNumbers = numbers
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
            val photoBytes = campScheduleService.loadPhoto(eventId, idx)
            val (name, partner) = effectiveNames(_state.value)
            val mySchedule = day?.let {
                computeMySchedule(it, name, _state.value.myGroupNumber, partner)
            } ?: emptyList()
            _state.value = _state.value.copy(
                day = day,
                photoBytes = photoBytes,
                mySchedule = mySchedule,
                isLoading = false
            )
        } catch (e: CancellationException) {
            throw e
        } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = AppError.generic(ex.message))
        }
    }

    /** Called once the Android capture pipeline has produced a [ScheduleDay] (and photo) for the selected date. */
    suspend fun saveDay(day: ScheduleDay, photoBytes: ByteArray) {
        val eventId = _state.value.eventId ?: return
        val date = _state.value.selectedDate ?: return
        val campStart = _state.value.campDates.firstOrNull() ?: return
        val idx = dayIndex(campStart, date)
        campScheduleService.saveDay(eventId, idx, day)
        campScheduleService.savePhoto(eventId, idx, photoBytes)
        val stored = _state.value.storedDayIndexes + idx
        val numbers = allAvailableGroupNumbers(eventId, stored)
        _state.value = _state.value.copy(storedDayIndexes = stored, availableGroupNumbers = numbers)
        selectDate(date)
        rescheduleReminders()
    }

    /** The group number is chosen once for the whole camp, not per day. */
    suspend fun setGroupNumber(number: Int) {
        val eventId = _state.value.eventId ?: return
        campScheduleService.saveGroupNumber(eventId, number)
        val (searchName, searchPartner) = effectiveNames(_state.value)
        val mySchedule = _state.value.day?.let {
            computeMySchedule(it, searchName, number, searchPartner)
        } ?: emptyList()
        _state.value = _state.value.copy(myGroupNumber = number, mySchedule = mySchedule)
        rescheduleReminders()
    }

    /** One shared reminder-minutes value applies to every stored day of this camp. */
    suspend fun setReminderMinutes(minutes: Int) {
        val eventId = _state.value.eventId ?: return
        campScheduleService.setReminderMinutes(eventId, minutes)
        _state.value = _state.value.copy(reminderMinutes = minutes)
        val campStart = _state.value.campDates.firstOrNull() ?: return
        val myGroupNumber = _state.value.myGroupNumber
        val (searchName, searchPartner) = effectiveNames(_state.value)
        campScheduleService.listStoredDayIndexes(eventId).forEach { idx ->
            val date = campStart.plus(DatePeriod(days = idx))
            val day = campScheduleService.loadDay(eventId, idx) ?: return@forEach
            val myEntries = myReminderTargets(day, searchName, myGroupNumber, searchPartner)
            campScheduleReminderService.rescheduleForDay(eventId, idx, date, myEntries, minutes, _state.value.eventName)
        }
    }

    private suspend fun rescheduleReminders() {
        val eventId = _state.value.eventId ?: return
        val date = _state.value.selectedDate ?: return
        val campStart = _state.value.campDates.firstOrNull() ?: return
        val idx = dayIndex(campStart, date)
        val day = _state.value.day ?: return
        val (searchName, searchPartner) = effectiveNames(_state.value)
        val myEntries = myReminderTargets(day, searchName, _state.value.myGroupNumber, searchPartner)
        campScheduleReminderService.rescheduleForDay(eventId, idx, date, myEntries, _state.value.reminderMinutes, _state.value.eventName)
    }

    private fun effectiveNames(state: CampScheduleState): Pair<String, String> =
        effectiveSearchNames(state.myGender, state.myTableName, state.myPartnerName)

    private suspend fun allAvailableGroupNumbers(eventId: Long, storedDayIndexes: Set<Int>): List<Int> =
        storedDayIndexes.mapNotNull { campScheduleService.loadDay(eventId, it) }
            .flatMap { availableGroupNumbers(it) }
            .distinct()
            .sorted()

    private fun accountGender(raw: String?): Gender? = when (raw) {
        "WOMAN" -> Gender.FEMALE
        "MAN" -> Gender.MALE
        else -> null
    }

    /** Fetches the logged-in account's [PersonDetails], refetching if the cache is missing the active-couples data needed for [resolvePartnerName]. */
    private suspend fun cachedPersonDetails(): PersonDetails? {
        val pid = try { userService.getCachedPersonId() } catch (e: CancellationException) { throw e } catch (_: Exception) { null } ?: return null
        val cachedJson = try { userService.getCachedPersonDetailsJson() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        val needsRefetch = cachedJson.isNullOrBlank() || !cachedJson.contains("activeCouplesList")
        if (needsRefetch) {
            try { userService.fetchAndStorePersonDetails(pid) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
        return try { userService.getCachedPersonDetails() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    }
}
