package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.notification.BirthdayNotificationSettings
import com.tkolymp.shared.people.Cohort
import com.tkolymp.shared.people.Person
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.launch

@Immutable
data class BirthdayNotificationState(
    /** Last saved settings (source of truth from storage) */
    val settings: BirthdayNotificationSettings = BirthdayNotificationSettings(),
    /** Working copy — edited by the user, not yet saved */
    val draft: BirthdayNotificationSettings = BirthdayNotificationSettings(),
    val availableGroups: List<Cohort> = emptyList(),
    /** All people except club trainers */
    val nonTrainerPeople: List<Person> = emptyList(),
    /** Person IDs that are club trainers */
    val trainerPersonIds: Set<String> = emptySet(),
    override val isLoading: Boolean = false,
    override val error: AppError? = null
) : ViewModelState

class BirthdayNotificationViewModel(
    private val notificationService: com.tkolymp.shared.notification.NotificationService = ServiceLocator.notificationService,
    private val peopleService: com.tkolymp.shared.people.PeopleService = ServiceLocator.peopleService,
    private val clubService: com.tkolymp.shared.club.ClubService = ServiceLocator.clubService
) : ViewModel() {
    private val _state = MutableStateFlow(BirthdayNotificationState())
    val state: StateFlow<BirthdayNotificationState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val settings = try {
                    notificationService.getBirthdaySettings()
                } catch (_: Exception) { BirthdayNotificationSettings() }

                val people = try {
                    peopleService.fetchPeople()
                } catch (_: Exception) { emptyList<Person>() }

                val groups = people
                    .flatMap { it.cohortMembershipsList.mapNotNull { m -> m.cohort } }
                    .filter { it.isVisible != false && it.id != null }
                    .distinctBy { it.id }
                    .sortedBy { it.name?.lowercase() }

                val trainerIds = try {
                    val club = clubService.fetchClubData()
                    club.trainers.mapNotNull { it.person?.id }.toSet()
                } catch (_: Exception) { emptySet() }

                val sorted = people.sortedBy { p ->
                    listOfNotNull(p.firstName, p.lastName).joinToString(" ").lowercase()
                }
                val nonTrainers = sorted.filter { it.id !in trainerIds }

                _state.value = _state.value.copy(
                    settings = settings,
                    draft = settings,
                    availableGroups = groups,
                    nonTrainerPeople = nonTrainers,
                    trainerPersonIds = trainerIds,
                    isLoading = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (ex: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = AppError.generic(ex.message))
            }
        }
    }

    fun updateDraft(draft: BirthdayNotificationSettings) {
        _state.value = _state.value.copy(draft = draft)
    }

    fun save() {
        val draft = _state.value.draft
        viewModelScope.launch {
            try {
                notificationService.saveBirthdaySettings(draft)
                _state.value = _state.value.copy(settings = draft)
            } catch (_: Exception) {}
        }
    }
}
