package com.tkolymp.shared.viewmodels

import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.cache.CacheService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OverviewState(
    val upcomingEvents: List<Any> = emptyList(),
    val recentAnnouncements: List<Any> = emptyList(),
    val myPersonId: String? = null,
    val myCoupleIds: List<String> = emptyList(),
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class OverviewViewModel(
    private val eventService: com.tkolymp.shared.event.IEventService = ServiceLocator.eventService,
    private val announcementService: com.tkolymp.shared.announcements.IAnnouncementService = ServiceLocator.announcementService,
    private val userService: com.tkolymp.shared.user.UserService = ServiceLocator.userService,
    private val cache: CacheService = ServiceLocator.cacheService
) {
    private val _state = MutableStateFlow(OverviewState())
    val state: StateFlow<OverviewState> = _state.asStateFlow()

    suspend fun loadOverview(startIso: String = "1970-01-01T00:00:00Z", endIso: String = "2100-01-01T00:00:00Z", forceRefresh: Boolean = false) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        if (forceRefresh) {
            try { cache.invalidatePrefix("events_") } catch (_: Throwable) {}
            try { cache.invalidatePrefix("announcements_") } catch (_: Throwable) {}
        }
        try {
            val events = try {
                val grouped = withContext(Dispatchers.Default) {
                    eventService.fetchEventsGroupedByDay(startIso, endIso, onlyMine = true, first = 200)
                }
                grouped.values.flatten()
            } catch (_: Throwable) { emptyList<Any>() }

            val announcements = try {
                withContext(Dispatchers.Default) { announcementService.getAnnouncements(false) }.take(3)
            } catch (_: Throwable) { emptyList<Any>() }

            val pid = try { userService.getCachedPersonId() } catch (_: Throwable) { null }
            val cids = try { userService.getCachedCoupleIds() } catch (_: Throwable) { emptyList<String>() }

            _state.value = _state.value.copy(
                upcomingEvents = events as? List<Any> ?: emptyList(),
                recentAnnouncements = announcements as? List<Any> ?: emptyList(),
                myPersonId = pid,
                myCoupleIds = cids,
                isLoading = false
            )
        } catch (ex: Throwable) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání přehledu")
        }
    }
}
