package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.Logger
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.announcements.Announcement
import com.tkolymp.shared.utils.DateRangeConstants
import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.event.EventInstance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OverviewState(
    val upcomingEvents: List<EventInstance> = emptyList(),
    val recentAnnouncements: List<Announcement> = emptyList(),
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
) : ViewModel() {
    private val _state = MutableStateFlow(OverviewState())
    val state: StateFlow<OverviewState> = _state.asStateFlow()

    suspend fun loadOverview(startIso: String = DateRangeConstants.FAR_PAST, endIso: String = DateRangeConstants.FAR_FUTURE, forceRefresh: Boolean = false) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        if (forceRefresh) {
            try { cache.invalidatePrefix("overview_") } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("OverviewViewModel", "cache invalidation failed: ${e.message}") }
            try { cache.invalidatePrefix("announcements_") } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("OverviewViewModel", "cache invalidation failed: ${e.message}") }
        }
        try {
            val events = try {
                    val grouped = withContext(Dispatchers.Default) {
                    eventService.fetchEventsGroupedByDay(startIso, endIso, onlyMine = true, first = 200, cacheNamespace = "overview_")
                }
                grouped.values.flatten()
            } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("OverviewViewModel", "fetchEvents failed: ${e.message}"); emptyList<EventInstance>() }

            val announcements = try {
                withContext(Dispatchers.Default) { announcementService.getAnnouncements(false) }.take(3)
            } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("OverviewViewModel", "getAnnouncements failed: ${e.message}"); emptyList<Announcement>() }

            val pid = try { userService.getCachedPersonId() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("OverviewViewModel", "getCachedPersonId failed: ${e.message}"); null }
            val cids = try { userService.getCachedCoupleIds() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("OverviewViewModel", "getCachedCoupleIds failed: ${e.message}"); emptyList<String>() }

            _state.value = _state.value.copy(
                upcomingEvents = events,
                recentAnnouncements = announcements,
                myPersonId = pid,
                myCoupleIds = cids,
                isLoading = false
            )
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání přehledu")
        }
    }
}
