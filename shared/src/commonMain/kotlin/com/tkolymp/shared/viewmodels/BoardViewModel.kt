package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.announcements.Announcement
import com.tkolymp.shared.announcements.AnnouncementBadge
import com.tkolymp.shared.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import com.tkolymp.shared.json.AppJson
import com.tkolymp.shared.language.AppStrings
import kotlin.time.Clock

data class BoardState(
    val selectedTab: Int = 0,
    val currentAnnouncements: List<Announcement> = emptyList(),
    val permanentAnnouncements: List<Announcement> = emptyList(),
    val isOffline: Boolean = false,
    val hasUnread: Boolean = false,
    override val isLoading: Boolean = false,
    override val error: AppError? = null
) : ViewModelState

class BoardViewModel : ViewModel() {
    private val announcementService by lazy { ServiceLocator.announcementService }
    private val cache by lazy { ServiceLocator.cacheService }
    private val badgeStorage by lazy { ServiceLocator.announcementBadgeStorage }

    private val _state = MutableStateFlow(BoardState())
    val state: StateFlow<BoardState> = _state.asStateFlow()

    init {
        checkUnreadBadge()
    }

    private fun checkUnreadBadge() {
        viewModelScope.launch {
            try {
                val lastSeen = try { badgeStorage.getLastSeenTimestamp() } catch (_: Exception) { null }
                val raw = try { ServiceLocator.offlineSyncManager.loadAnnouncements(sticky = false) } catch (_: Exception) { null }
                if (raw != null) {
                    val parsed = AppJson.decodeFromString(ListSerializer(Announcement.serializer()), raw)
                    val latestTs = parsed.mapNotNull { it.updatedAt ?: it.createdAt }.maxOrNull()
                    val unread = latestTs != null && (lastSeen == null || latestTs > lastSeen)
                    if (unread) {
                        _state.value = _state.value.copy(hasUnread = true)
                        AnnouncementBadge.set(true)
                    }
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
    }

    fun markAsSeen() {
        viewModelScope.launch {
            try {
                val now = Clock.System.now().toString()
                badgeStorage.setLastSeenTimestamp(now)
                _state.value = _state.value.copy(hasUnread = false)
                AnnouncementBadge.set(false)
            } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
    }

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
            var list: List<Announcement>? = null

            // If not forcing refresh, try offline cached announcements first and show them immediately
            if (!forceRefresh) {
                try {
                    val raw = ServiceLocator.offlineSyncManager.loadAnnouncements(sticky)
                    if (raw != null) {
                        val parsed = AppJson.decodeFromString(ListSerializer(Announcement.serializer()), raw)
                        val parsedSorted = parsed.sortedByDescending { it.updatedAt ?: it.createdAt ?: "" }
                        if (sticky) {
                            _state.value = _state.value.copy(permanentAnnouncements = parsedSorted, isOffline = true, isLoading = false)
                        } else {
                            val lastSeen = try { badgeStorage.getLastSeenTimestamp() } catch (_: Exception) { null }
                            val latestTs = parsedSorted.mapNotNull { it.updatedAt ?: it.createdAt }.maxOrNull()
                            val unread = latestTs != null && (lastSeen == null || latestTs > lastSeen)
                            _state.value = _state.value.copy(currentAnnouncements = parsedSorted, isOffline = true, isLoading = false, hasUnread = unread)
                            AnnouncementBadge.set(unread)
                        }
                        Logger.d("BoardViewModel", "loaded offline announcements for sticky=$sticky count=${parsedSorted.size}")
                        list = parsedSorted
                    }
                } catch (e: CancellationException) { throw e } catch (_: Exception) {}
            }

            try {
                val fetched = withContext(Dispatchers.Default) { announcementService.getAnnouncements(sticky) }
                    .sortedByDescending { it.updatedAt ?: it.createdAt ?: "" }
                list = fetched
                if (sticky) {
                    _state.value = _state.value.copy(permanentAnnouncements = fetched, isOffline = false, isLoading = false)
                } else {
                    val lastSeen = try { badgeStorage.getLastSeenTimestamp() } catch (_: Exception) { null }
                    val latestTs = fetched.mapNotNull { it.updatedAt ?: it.createdAt }.maxOrNull()
                    val unread = latestTs != null && (lastSeen == null || latestTs > lastSeen)
                    _state.value = _state.value.copy(currentAnnouncements = fetched, isOffline = false, isLoading = false, hasUnread = unread)
                    AnnouncementBadge.set(unread)
                }
                Logger.d("BoardViewModel", "fetched online announcements for sticky=$sticky count=${fetched.size}")
            } catch (e: CancellationException) { throw e } catch (netEx: Exception) {
                if (list != null && list.isNotEmpty()) {
                    _state.value = _state.value.copy(isOffline = true, isLoading = false)
                } else {
                    try {
                        val raw = ServiceLocator.offlineSyncManager.loadAnnouncements(sticky)
                        if (raw != null) {
                            val parsed = AppJson.decodeFromString(ListSerializer(Announcement.serializer()), raw)
                            val parsedSorted = parsed.sortedByDescending { it.updatedAt ?: it.createdAt ?: "" }
                            if (sticky) {
                                _state.value = _state.value.copy(permanentAnnouncements = parsedSorted, isOffline = true, isLoading = false)
                            } else {
                                val lastSeen = try { badgeStorage.getLastSeenTimestamp() } catch (_: Exception) { null }
                                val latestTs = parsedSorted.mapNotNull { it.updatedAt ?: it.createdAt }.maxOrNull()
                                val unread = latestTs != null && (lastSeen == null || latestTs > lastSeen)
                                _state.value = _state.value.copy(currentAnnouncements = parsedSorted, isOffline = true, isLoading = false, hasUnread = unread)
                                AnnouncementBadge.set(unread)
                            }
                            Logger.d("BoardViewModel", "fallback offline announcements for sticky=$sticky count=${parsedSorted.size}")
                        } else {
                            if (sticky) _state.value = _state.value.copy(permanentAnnouncements = previousPermanent, isLoading = false, error = AppError.generic(netEx.message ?: AppStrings.current.errorMessages.errorLoading))
                            else _state.value = _state.value.copy(currentAnnouncements = previousCurrent, isLoading = false, error = AppError.generic(netEx.message ?: AppStrings.current.errorMessages.errorLoading))
                        }
                    } catch (_: Exception) {
                        if (sticky) _state.value = _state.value.copy(permanentAnnouncements = previousPermanent, isLoading = false, error = AppError.generic(netEx.message ?: AppStrings.current.errorMessages.errorLoading))
                        else _state.value = _state.value.copy(currentAnnouncements = previousCurrent, isLoading = false, error = AppError.generic(netEx.message ?: AppStrings.current.errorMessages.errorLoading))
                    }
                }
            }
        Logger.d("BoardViewModel", "loadAnnouncements end: selectedTab=${_state.value.selectedTab} current=${_state.value.currentAnnouncements.size} permanent=${_state.value.permanentAnnouncements.size} isOffline=${_state.value.isOffline}")
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            _state.value = _state.value.copy(currentAnnouncements = previousCurrent, permanentAnnouncements = previousPermanent, isLoading = false, error = AppError.generic(ex.message ?: AppStrings.current.errorMessages.errorLoading))
        }
    }

    fun selectTab(index: Int) {
        _state.value = _state.value.copy(selectedTab = index)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
