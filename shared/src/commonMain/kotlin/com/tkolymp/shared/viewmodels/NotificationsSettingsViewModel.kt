package com.tkolymp.shared.viewmodels

import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationsSettingsState(
    val enabledCategories: Set<String> = emptySet(),
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class NotificationsSettingsViewModel(
    private val notificationService: com.tkolymp.shared.notification.NotificationService = ServiceLocator.notificationService
) {
    private val _state = MutableStateFlow(NotificationsSettingsState())
    val state: StateFlow<NotificationsSettingsState> = _state.asStateFlow()

    suspend fun loadSettings() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            val settings = try { notificationService.getSettings() } catch (_: Throwable) { null }
            val prefs = settings?.rules?.flatMap { rule -> rule.types }.orEmpty().toSet()
            _state.value = _state.value.copy(enabledCategories = prefs, isLoading = false)
        } catch (ex: Throwable) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání nastavení")
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
            _state.value = _state.value.copy(enabledCategories = new)
        } catch (ex: Throwable) {
            _state.value = _state.value.copy(error = ex.message ?: "Chyba při aktualizaci")
        }
    }
}
