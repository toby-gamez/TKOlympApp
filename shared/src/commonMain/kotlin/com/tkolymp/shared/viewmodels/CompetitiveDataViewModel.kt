package com.tkolymp.shared.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.competitivedata.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** Organization list screen ("Taneční organizace"). */
@Immutable
data class DanceOrganizationsState(
    val federations: List<DanceFederation> = emptyList(),
    override val isLoading: Boolean = false,
    override val error: AppError? = null
) : ViewModelState

class DanceOrganizationsViewModel(
    private val service: ICompetitiveDataService = ServiceLocator.competitiveDataService
) : ViewModel() {
    private val _state = MutableStateFlow(DanceOrganizationsState())
    val state: StateFlow<DanceOrganizationsState> = _state.asStateFlow()

    suspend fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        try {
            val list = service.getFederations()
            // Never let an empty "success" wipe data we already have.
            _state.update { it.copy(federations = list.ifEmpty { it.federations }, isLoading = false) }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, error = AppError.network(e.message, e)) }
        }
    }
}

/**
 * UPCOMING = everything from today on (across the year boundary), soonest first; PAST = everything already over,
 * most recent first; YEAR = the selected calendar year, oldest first.
 */
enum class EventsViewMode { UPCOMING, PAST, YEAR }

@Immutable
data class FederationEventsState(
    val federationCode: String = "",
    val federationName: String = "",
    val years: List<FederationYear> = emptyList(),
    val year: Int? = null,
    val viewMode: EventsViewMode = EventsViewMode.UPCOMING,
    /** 1..12, null = whole year. */
    val month: Int? = null,
    val query: String = "",
    /** Competition types (CUP, ...) to keep; an event matches when it has a competition of any selected type. */
    val selectedTypes: Set<String> = emptySet(),
    /** The year's events, oldest first. */
    val allEvents: List<FederationEventSummary> = emptyList(),
    val showMap: Boolean = false,
    /** The shown events are the saved copy because the live request failed. */
    val isOffline: Boolean = false,
    /** Events after every filter including the month, oldest first. Derived - see [recomputed]. */
    val events: List<FederationEventSummary> = emptyList(),
    /** Event counts per month (1..12) before the month filter, for the month chips. Derived. */
    val monthCounts: Map<Int, Int> = emptyMap(),
    /** Events per competition type among the loaded ones, in the API's type order. Derived. */
    val typeCounts: Map<String, Int> = emptyMap(),
    override val isLoading: Boolean = false,
    override val error: AppError? = null
) : ViewModelState {
    /** Recomputes the derived fields once per change instead of on every read (the UI reads them a lot). */
    fun recomputed(): FederationEventsState {
        val byFilters = allEvents.filter { e ->
            !e.isCancelled &&
                (selectedTypes.isEmpty() || e.types.any { it in selectedTypes }) &&
                (query.isBlank() || e.matches(query))
        }
        val visible = allEvents.filter { !it.isCancelled }
        return copy(
            events = byFilters.filter { month == null || it.month == month },
            monthCounts = byFilters.groupingBy { it.month }.eachCount(),
            typeCounts = visible.flatMap { it.types }.groupingBy { it }.eachCount()
                .toList().sortedBy { (t, _) -> TYPE_ORDER.indexOf(t).let { if (it < 0) Int.MAX_VALUE else it } }.toMap()
        )
    }
}

private val TYPE_ORDER = listOf("CUP", "RANKING", "LEAGUE", "CHAMPIONSHIP", "TOP_LEVEL", "SUPER_LEAGUE", "G_CUP")

private fun FederationEventSummary.matches(q: String): Boolean {
    val needle = q.trim().lowercase()
    return listOfNotNull(name, place, country).any { it.lowercase().contains(needle) }
}

class FederationEventsViewModel(
    private val service: ICompetitiveDataService = ServiceLocator.competitiveDataService
) : ViewModel() {
    private val _state = MutableStateFlow(FederationEventsState())
    val state: StateFlow<FederationEventsState> = _state.asStateFlow()

    private fun today() = Clock.System.todayIn(TimeZone.currentSystemDefault())

    suspend fun load(code: String, forceRefresh: Boolean = false) {
        if (!forceRefresh && _state.value.federationCode == code && _state.value.allEvents.isNotEmpty()) return
        _state.update { if (it.federationCode == code) it.copy(isLoading = true, error = null) else FederationEventsState(federationCode = code, isLoading = true) }
        try {
            val name = service.getFederations().firstOrNull { it.code == code }?.name
            val years = service.getYears(code).ifEmpty { _state.value.years }
            val thisYear = today().year
            val year = _state.value.year ?: years.map { it.year }.let { ys -> if (thisYear in ys) thisYear else ys.lastOrNull() } ?: thisYear
            _state.update { it.copy(years = years, year = year, federationName = name ?: it.federationName).recomputed() }
            loadSource(forceRefresh)
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, error = AppError.network(e.message, e)) }
        }
    }

    suspend fun selectYear(year: Int) {
        if (_state.value.year == year && _state.value.viewMode == EventsViewMode.YEAR) return
        _state.update { it.copy(year = year, month = null, isLoading = true, error = null, allEvents = emptyList()).recomputed() }
        reload()
    }

    suspend fun setViewMode(mode: EventsViewMode) {
        if (_state.value.viewMode == mode) return
        _state.update { it.copy(viewMode = mode, month = null, isLoading = true, error = null, allEvents = emptyList()).recomputed() }
        reload()
    }

    private suspend fun reload() {
        try { loadSource(false) } catch (e: CancellationException) { throw e } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, error = AppError.network(e.message, e)) }
        }
    }

    private suspend fun loadSource(forceRefresh: Boolean) {
        val code = _state.value.federationCode
        val mode = _state.value.viewMode
        val year = _state.value.year
        val loaded = when (mode) {
            EventsViewMode.YEAR -> service.getEvents(code, year ?: today().year, forceRefresh)
            EventsViewMode.UPCOMING -> {
                val now = today()
                val todayIso = now.toString()
                // An event that is still running counts as upcoming.
                val parts = listOf(service.getEvents(code, now.year, forceRefresh), service.getEvents(code, now.year + 1, forceRefresh))
                LoadedData(
                    parts.flatMap { it.data }
                        .filter { (it.endDate ?: it.startDate) >= todayIso }
                        .sortedWith(compareBy({ it.startDate }, { it.name })),
                    fromOffline = parts.any { it.fromOffline }
                )
            }
            EventsViewMode.PAST -> {
                val now = today()
                val todayIso = now.toString()
                val years = _state.value.years.map { it.year }.filter { it <= now.year }.ifEmpty { listOf(now.year) }
                val parts = coroutineScope { years.map { y -> async { service.getEvents(code, y, forceRefresh) } }.awaitAll() }
                LoadedData(
                    parts.flatMap { it.data }
                        .filter { (it.endDate ?: it.startDate) < todayIso }
                        .sortedWith(compareByDescending<FederationEventSummary> { it.startDate }.thenBy { it.name }),
                    fromOffline = parts.any { it.fromOffline }
                )
            }
        }
        val events = loaded.data
        // Ignore a response for a view the user already navigated away from.
        _state.update { if (it.viewMode == mode && (mode != EventsViewMode.YEAR || it.year == year)) it.copy(allEvents = events, isOffline = loaded.fromOffline, isLoading = false).recomputed() else it }
    }

    fun selectMonth(month: Int?) = _state.update { it.copy(month = month).recomputed() }
    fun setQuery(q: String) = _state.update { it.copy(query = q).recomputed() }
    fun toggleType(type: String) = _state.update { it.copy(selectedTypes = if (type in it.selectedTypes) it.selectedTypes - type else it.selectedTypes + type).recomputed() }
    fun setShowMap(show: Boolean) = _state.update { it.copy(showMap = show) }
}

@Immutable
data class FederationEventDetailState(
    val event: FederationEventDetail? = null,
    val results: Map<Long, FederationCompetitionResults> = emptyMap(),
    val loadingResults: Set<Long> = emptySet(),
    override val isLoading: Boolean = false,
    override val error: AppError? = null
) : ViewModelState

class FederationEventDetailViewModel(
    private val service: ICompetitiveDataService = ServiceLocator.competitiveDataService
) : ViewModel() {
    private val _state = MutableStateFlow(FederationEventDetailState())
    val state: StateFlow<FederationEventDetailState> = _state.asStateFlow()

    suspend fun load(eventId: Long) {
        _state.update { it.copy(isLoading = true, error = null) }
        try {
            val e = service.getEventDetail(eventId)
            _state.update { it.copy(event = e ?: it.event, isLoading = false, error = if (e == null && it.event == null) AppError.notFound(null) else null) }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, error = AppError.network(e.message, e)) }
        }
    }

    suspend fun loadResults(competitionId: Long) {
        if (competitionId in _state.value.results || competitionId in _state.value.loadingResults) return
        _state.update { it.copy(loadingResults = it.loadingResults + competitionId) }
        try {
            val r = service.getCompetitionResults(competitionId)
            _state.update { it.copy(results = if (r != null) it.results + (competitionId to r) else it.results, loadingResults = it.loadingResults - competitionId) }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            _state.update { it.copy(loadingResults = it.loadingResults - competitionId, error = AppError.network(e.message, e)) }
        }
    }
}
