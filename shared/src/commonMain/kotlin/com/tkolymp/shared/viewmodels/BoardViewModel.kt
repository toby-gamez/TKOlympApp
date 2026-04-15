package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class BoardState(
    val selectedTab: Int = 0,
    val currentAnnouncements: List<Any> = emptyList(),
    val permanentAnnouncements: List<Any> = emptyList(),
    val isOffline: Boolean = false,
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class BoardViewModel(
    private val announcementService: com.tkolymp.shared.announcements.IAnnouncementService = ServiceLocator.announcementService,
    private val cache: CacheService = ServiceLocator.cacheService
) : ViewModel() {
    private val _state = MutableStateFlow(BoardState())
    val state: StateFlow<BoardState> = _state.asStateFlow()

    suspend fun loadAnnouncements(forceRefresh: Boolean = false) {
        // preserve current + permanent announcements while loading so UI doesn't go empty
        val previousCurrent = _state.value.currentAnnouncements
        val previousPermanent = _state.value.permanentAnnouncements
        _state.value = _state.value.copy(isLoading = true, error = null)

        if (forceRefresh) {
            try {
                val online = try { ServiceLocator.networkMonitor.isConnected() } catch (_: Exception) { true }
                if (online) cache.invalidatePrefix("announcements_") else Logger.d("BoardViewModel", "skipping announcements cache invalidation: offline")
            } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }

        Logger.d("BoardViewModel", "loadAnnouncements start: selectedTab=${_state.value.selectedTab} forceRefresh=$forceRefresh current=${previousCurrent.size} permanent=${previousPermanent.size}")

        try {
            val sticky = _state.value.selectedTab == 1
            var list: List<com.tkolymp.shared.announcements.Announcement>? = null

            // If not forcing refresh, try offline cached announcements first and show them immediately
            if (!forceRefresh) {
                try {
                    val raw = ServiceLocator.offlineSyncManager.loadAnnouncements(sticky)
                    if (raw != null) {
                        val parsed = Json.decodeFromString(ListSerializer(com.tkolymp.shared.announcements.Announcement.serializer()), raw)
                        val parsedSorted = parsed.sortedByDescending { it.updatedAt ?: it.createdAt ?: "" }
                        if (sticky) {
                            _state.value = _state.value.copy(permanentAnnouncements = parsedSorted as? List<Any> ?: emptyList(), isOffline = true, isLoading = false)
                        } else {
                            _state.value = _state.value.copy(currentAnnouncements = parsedSorted as? List<Any> ?: emptyList(), isOffline = true, isLoading = false)
                        }
                        Logger.d("BoardViewModel", "loaded offline announcements for sticky=$sticky count=${parsedSorted.size}")
                        // keep parsed as base; still attempt network refresh below
                        list = parsedSorted
                    }
                } catch (e: CancellationException) { throw e } catch (_: Exception) {}
            }

            try {
                // Attempt network fetch (will overwrite cached view on success)
                val fetched = withContext(Dispatchers.Default) { announcementService.getAnnouncements(sticky) }
                    .sortedByDescending { it.updatedAt ?: it.createdAt ?: "" }
                list = fetched
                // update state with fresh online data into the appropriate bucket
                if (sticky) {
                    _state.value = _state.value.copy(permanentAnnouncements = fetched as? List<Any> ?: emptyList(), isOffline = false, isLoading = false)
                } else {
                    _state.value = _state.value.copy(currentAnnouncements = fetched as? List<Any> ?: emptyList(), isOffline = false, isLoading = false)
                }
                Logger.d("BoardViewModel", "fetched online announcements for sticky=$sticky count=${fetched.size}")
            } catch (e: CancellationException) { throw e } catch (netEx: Exception) {
                // If network failed and we have already shown cached data, keep it; otherwise fallback to offline manager
                if (list != null && list.isNotEmpty()) {
                    _state.value = _state.value.copy(isOffline = true, isLoading = false)
                } else {
                    try {
                        val raw = ServiceLocator.offlineSyncManager.loadAnnouncements(sticky)
                        if (raw != null) {
                            val parsed = Json.decodeFromString(ListSerializer(com.tkolymp.shared.announcements.Announcement.serializer()), raw)
                            val parsedSorted = parsed.sortedByDescending { it.updatedAt ?: it.createdAt ?: "" }
                            if (sticky) {
                                _state.value = _state.value.copy(permanentAnnouncements = parsedSorted as? List<Any> ?: emptyList(), isOffline = true, isLoading = false)
                            } else {
                                _state.value = _state.value.copy(currentAnnouncements = parsedSorted as? List<Any> ?: emptyList(), isOffline = true, isLoading = false)
                            }
                            Logger.d("BoardViewModel", "fallback offline announcements for sticky=$sticky count=${parsedSorted.size}")
                        } else {
                            // restore previous appropriate list
                            if (sticky) _state.value = _state.value.copy(permanentAnnouncements = previousPermanent, isLoading = false, error = netEx.message ?: "Chyba při načítání")
                            else _state.value = _state.value.copy(currentAnnouncements = previousCurrent, isLoading = false, error = netEx.message ?: "Chyba při načítání")
                        }
                    } catch (_: Exception) {
                        if (sticky) _state.value = _state.value.copy(permanentAnnouncements = previousPermanent, isLoading = false, error = netEx.message ?: "Chyba při načítání")
                        else _state.value = _state.value.copy(currentAnnouncements = previousCurrent, isLoading = false, error = netEx.message ?: "Chyba při načítání")
                    }
                }
            }
        Logger.d("BoardViewModel", "loadAnnouncements end: selectedTab=${_state.value.selectedTab} current=${_state.value.currentAnnouncements.size} permanent=${_state.value.permanentAnnouncements.size} isOffline=${_state.value.isOffline}")
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            // restore previous appropriate lists on fatal error
            _state.value = _state.value.copy(currentAnnouncements = previousCurrent, permanentAnnouncements = previousPermanent, isLoading = false, error = ex.message ?: "Chyba při načítání")
        }
    }

    fun selectTab(index: Int) {
        _state.value = _state.value.copy(selectedTab = index)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
