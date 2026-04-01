package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.tkolymp.shared.people.Person
import com.tkolymp.shared.notification.ReceivedMessage

data class NotificationsSettingsState(
    val enabledCategories: Set<String> = emptySet(),
    val settings: com.tkolymp.shared.notification.NotificationSettings? = null,
    val availableGroups: List<Pair<String, String>> = emptyList(),
    val myCohortIds: Set<String> = emptySet(),
    val coachMessages: List<ReceivedMessage> = emptyList(),
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class NotificationsSettingsViewModel(
    private val notificationService: com.tkolymp.shared.notification.NotificationService = ServiceLocator.notificationService,
    private val peopleService: com.tkolymp.shared.people.PeopleService = ServiceLocator.peopleService,
    private val userService: com.tkolymp.shared.user.UserService = ServiceLocator.userService,
    private val notificationStorage: com.tkolymp.shared.notification.NotificationStorage = ServiceLocator.notificationStorage
) : ViewModel() {
    private val _state = MutableStateFlow(NotificationsSettingsState())
    val state: StateFlow<NotificationsSettingsState> = _state.asStateFlow()

    suspend fun loadSettings() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            val settings = try { notificationService.getSettings() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            val prefs = settings?.rules?.flatMap { rule -> rule.types }.orEmpty().toSet()
            _state.value = _state.value.copy(enabledCategories = prefs, settings = settings, isLoading = false)
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání nastavení")
        }
    }

    suspend fun loadUiData() {
        // load visible cohorts/groups and user's cohort ids and received messages
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            // ensure cached person details exist (similar to ProfileViewModel)
            try {
                val pid = try { userService.getCachedPersonId() } catch (_: Exception) { null }
                if (!pid.isNullOrBlank()) {
                    val cachedPerson = try { userService.getCachedPersonDetails() } catch (_: Exception) { null }
                    val cachedPersonJson = try { userService.getCachedPersonDetailsJson() } catch (_: Exception) { null }
                    val needsRefetch = cachedPerson == null || cachedPersonJson.isNullOrBlank() || !(
                        cachedPersonJson.contains("activeCouplesList") &&
                            cachedPersonJson.contains("cohortMembershipsList")
                        )
                    if (needsRefetch) {
                        try { userService.fetchAndStorePersonDetails(pid) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
                    }
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) {}

            val people = try { peopleService.fetchPeople() } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList<Person>() }
            val groups = people.flatMap { p -> p.cohortMembershipsList.mapNotNull { it.cohort } }
                .filter { it.isVisible == true }
                .distinctBy { it.id }
                .mapNotNull { c -> c.id?.let { id -> Pair(id, c.name ?: id) } }

            val me = try { userService.getCachedPersonDetails() } catch (_: Exception) { null }
            val myIds = me?.cohortMembershipsList?.mapNotNull { it.cohort?.id }?.toSet() ?: emptySet()

            val msgs = try { notificationStorage.getReceivedNotifications() } catch (_: Exception) { emptyList<ReceivedMessage>() }

            // debug: log loaded messages and their topics
            try {
                msgs.forEach { m -> com.tkolymp.shared.Logger.d("NotificationsSettingsVM", "loaded msg id=${m.id} topic=${m.topic} title=${m.title}") }
            } catch (_: Exception) {}

            // update topic subscriptions: always subscribe to "all" plus my cohort ids
            try {
                val subscribe = myIds + "all"
                val allGroupIds = groups.map { it.first }.toSet()
                val unsubscribe = allGroupIds - myIds
                ServiceLocator.topicManager?.updateSubscriptions(subscribe, unsubscribe)
            } catch (_: Exception) {
                // ignore if platform topic manager not available
            }

            _state.value = _state.value.copy(availableGroups = groups, myCohortIds = myIds, coachMessages = msgs, isLoading = false)
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání dat")
        }
    }

    suspend fun toggleCategory(category: String) {
        val current = _state.value.enabledCategories
        val new = if (current.contains(category)) current - category else current + category
        try {
            val settings = notificationService.getSettings() ?: com.tkolymp.shared.notification.NotificationSettings(globalEnabled = true, rules = listOf())
            // naive update: replace rules with a single rule containing selected types
            val newSettings = settings.copy(rules = listOf(com.tkolymp.shared.notification.NotificationRule(id = kotlin.random.Random.Default.nextLong().toString(), name = "types", enabled = true, filterType = com.tkolymp.shared.notification.FilterType.BY_TYPE, locations = listOf(), trainers = listOf(), types = new.toList(), timesBeforeMinutes = listOf(60))))
            notificationService.updateSettings(newSettings)
            _state.value = _state.value.copy(enabledCategories = new, settings = newSettings)
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            _state.value = _state.value.copy(error = ex.message ?: "Chyba při aktualizaci")
        }
    }
}
