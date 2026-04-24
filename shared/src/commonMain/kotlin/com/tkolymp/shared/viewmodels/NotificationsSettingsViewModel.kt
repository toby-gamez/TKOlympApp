package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.Logger
import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import com.tkolymp.shared.people.Person
import com.tkolymp.shared.notification.EventReminder
import com.tkolymp.shared.notification.FilterType
import com.tkolymp.shared.notification.NotificationRule
import com.tkolymp.shared.notification.NotificationSettings
import com.tkolymp.shared.notification.ReceivedMessage

data class NotificationsSettingsState(
    val enabledCategories: Set<String> = emptySet(),
    val settings: NotificationSettings? = null,
    // derived from settings — used directly by screen (no local copy)
    val rules: List<NotificationRule> = emptyList(),
    val globalEnabled: Boolean = true,
    // club data for rule dialogs
    val availableLocations: List<String> = emptyList(),
    val availableTrainers: List<Pair<String, String>> = emptyList(),
    // UI data
    val availableGroups: List<Pair<String, String>> = emptyList(),
    val myCohortIds: Set<String> = emptySet(),
    val coachMessages: List<ReceivedMessage> = emptyList(),
    val reminders: List<EventReminder> = emptyList(),
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class NotificationsSettingsViewModel(
    private val notificationService: com.tkolymp.shared.notification.NotificationService = ServiceLocator.notificationService,
    private val peopleService: com.tkolymp.shared.people.PeopleService = ServiceLocator.peopleService,
    private val userService: com.tkolymp.shared.user.UserService = ServiceLocator.userService,
    private val clubService: com.tkolymp.shared.club.ClubService = ServiceLocator.clubService,
    private val notificationStorage: com.tkolymp.shared.notification.NotificationStorage = ServiceLocator.notificationStorage,
    private val personalEventService: com.tkolymp.shared.personalevents.PersonalEventService = ServiceLocator.personalEventService
) : ViewModel() {
    private val _state = MutableStateFlow(NotificationsSettingsState())
    val state: StateFlow<NotificationsSettingsState> = _state.asStateFlow()

    suspend fun loadSettings() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            val settings = try { notificationService.getSettings() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            val prefs = settings?.rules?.flatMap { rule -> rule.types }.orEmpty().toSet()
            _state.value = _state.value.copy(
                enabledCategories = prefs,
                settings = settings,
                rules = settings?.rules ?: emptyList(),
                globalEnabled = settings?.globalEnabled ?: true,
                isLoading = false
            )
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání nastavení")
        }
    }

    suspend fun loadClubData() {
        try {
            val club = withContext(Dispatchers.Default) { clubService.fetchClubData() }
            val locations = club.locations.mapNotNull { it.name?.trim() }
                .filter { it.isNotBlank() && !it.equals("ZRUŠENO", ignoreCase = true) }
                .distinct()
            val trainers = club.trainers.mapNotNull { t ->
                t.person?.let { p ->
                    val name = listOfNotNull(p.firstName, p.lastName).joinToString(" ").trim()
                    val id = p.id ?: return@mapNotNull null
                    if (name.isNotBlank()) Pair(id, name) else null
                }
            }.distinct()
            _state.value = _state.value.copy(availableLocations = locations, availableTrainers = trainers)
        } catch (e: CancellationException) { throw e } catch (_: Exception) {}
    }

    suspend fun loadUiData() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
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

            try {
                val subscribe = myIds + "all"
                val allGroupIds = groups.map { it.first }.toSet()
                val unsubscribe = allGroupIds - myIds
                ServiceLocator.topicManager?.updateSubscriptions(subscribe, unsubscribe)
            } catch (_: Exception) {}

            _state.value = _state.value.copy(availableGroups = groups, myCohortIds = myIds, coachMessages = msgs, isLoading = false)
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání dat")
        }
    }

    suspend fun setGlobalEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(globalEnabled = enabled)
        saveCurrentSettings()
    }

    suspend fun addOrUpdateRule(rule: NotificationRule) {
        val existingIdx = _state.value.rules.indexOfFirst { it.id == rule.id }
        val newRules = if (existingIdx >= 0) {
            _state.value.rules.toMutableList().also { it[existingIdx] = rule }
        } else {
            _state.value.rules + rule
        }
        _state.value = _state.value.copy(rules = newRules)
        saveCurrentSettings()
    }

    suspend fun deleteRule(id: String) {
        _state.value = _state.value.copy(rules = _state.value.rules.filter { it.id != id })
        saveCurrentSettings()
    }

    suspend fun toggleRule(id: String, enabled: Boolean) {
        val newRules = _state.value.rules.map { if (it.id == id) it.copy(enabled = enabled) else it }
        _state.value = _state.value.copy(rules = newRules)
        saveCurrentSettings()
    }

    suspend fun importSettings(settings: NotificationSettings) {
        _state.value = _state.value.copy(rules = settings.rules, globalEnabled = settings.globalEnabled)
        saveCurrentSettings()
    }

    private suspend fun saveCurrentSettings() {
        try {
            val settings = NotificationSettings(
                globalEnabled = _state.value.globalEnabled,
                rules = _state.value.rules
            )
            notificationService.updateSettings(settings)
            _state.value = _state.value.copy(settings = settings)
            try { personalEventService.rescheduleAllPersonalEvents() } catch (_: Exception) {}
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            Logger.d("NotificationsSettingsVM", "Failed to save settings: ${ex.message}")
        }
    }

    suspend fun loadReminders() {
        try {
            val reminders = notificationService.getReminders()
            _state.value = _state.value.copy(reminders = reminders)
        } catch (e: CancellationException) { throw e } catch (_: Exception) {}
    }

    suspend fun deleteReminder(id: String) {
        try {
            notificationService.deleteReminder(id)
            _state.value = _state.value.copy(reminders = _state.value.reminders.filter { it.id != id })
        } catch (e: CancellationException) { throw e } catch (_: Exception) {}
    }

    suspend fun updateReminderMinutes(reminder: EventReminder, minutesBefore: Int) {
        try {
            val updated = notificationService.addOrUpdateReminder(reminder.copy(minutesBefore = minutesBefore))
            val newList = _state.value.reminders.map { if (it.id == reminder.id) updated else it }
            _state.value = _state.value.copy(reminders = newList)
        } catch (e: CancellationException) { throw e } catch (_: Exception) {}
    }

    suspend fun toggleCategory(category: String) {
        val current = _state.value.enabledCategories
        val new = if (current.contains(category)) current - category else current + category
        try {
            val settings = notificationService.getSettings() ?: NotificationSettings(globalEnabled = true, rules = listOf())
            val newSettings = settings.copy(rules = listOf(NotificationRule(
                id = kotlin.random.Random.Default.nextLong().toString(),
                name = "types", enabled = true,
                filterType = FilterType.BY_TYPE,
                locations = listOf(), trainers = listOf(),
                types = new.toList(), timesBeforeMinutes = listOf(60)
            )))
            notificationService.updateSettings(newSettings)
            _state.value = _state.value.copy(enabledCategories = new, settings = newSettings,
                rules = newSettings.rules, globalEnabled = newSettings.globalEnabled)
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            _state.value = _state.value.copy(error = ex.message ?: "Chyba při aktualizaci")
        }
    }
}

