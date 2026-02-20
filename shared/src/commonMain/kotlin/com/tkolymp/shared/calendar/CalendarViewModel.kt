package com.tkolymp.shared.calendar

import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.event.IEventService
import com.tkolymp.shared.user.UserService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.*

/**
 * ViewModel for CalendarView screen
 * Manages state, loads events, and calculates layouts
 */
class CalendarViewModel(
    private val eventService: IEventService = ServiceLocator.eventService,
    private val userService: UserService = ServiceLocator.userService
) {
    private val _state = MutableStateFlow(
        CalendarViewState(
            selectedDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
            viewMode = ViewMode.DAY
        )
    )
    val state: StateFlow<CalendarViewState> = _state.asStateFlow()
    
    private var myPersonId: String? = null
    private var myCoupleIds: List<String> = emptyList()
    private var userInfoLoaded = false
    
    /**
     * Load cached user information (lazy loading)
     */
    private suspend fun loadUserInfo() {
        if (userInfoLoaded) return
        
        try {
            myPersonId = userService.getCachedPersonId()
        } catch (e: Exception) {
            myPersonId = null
        }
        
        try {
            myCoupleIds = userService.getCachedCoupleIds()
        } catch (e: Exception) {
            myCoupleIds = emptyList()
        }
        
        userInfoLoaded = true
    }
    
    /**
     * Load events for the current date range and view mode
     */
    suspend fun loadEvents() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        
        // Ensure user info is loaded first
        loadUserInfo()
        
        try {
            val currentState = _state.value
            val timeRange = CalendarUtils.calculateTimeRange(
                currentState.selectedDate, 
                currentState.viewMode
            )
            
            // Convert to ISO strings for API
            val startIso = timeRange.start.toInstant(TimeZone.currentSystemDefault()).toString()
            val endIso = timeRange.end.toInstant(TimeZone.currentSystemDefault()).toString()
            
            // Fetch from API
            val eventsGrouped = eventService.fetchEventsGroupedByDay(
                startRangeIso = startIso,
                endRangeIso = endIso,
                onlyMine = currentState.showOnlyMine,
                first = 500,
                offset = 0,
                onlyType = null,
                cacheNamespace = "calendar_"
            )
            
            // Convert to TimelineEvents
            val allTimelineEvents = eventsGrouped.values.flatten()
                .mapNotNull { instance ->
                    CalendarUtils.eventInstanceToTimelineEvent(
                        instance,
                        myPersonId,
                        myCoupleIds
                    )
                }
                .filter { event ->
                    // Filter by date range
                    event.startTime.date >= timeRange.start.date &&
                    event.startTime.date <= timeRange.end.date
                }
            
            // Calculate layout for all events
            val layoutData = if (currentState.viewMode == ViewMode.DAY) {
                // For single day, calculate layout for all events
                CollisionDetectionAlgorithm.calculateLayout(allTimelineEvents)
            } else {
                // For multi-day, calculate layout per day
                allTimelineEvents.groupBy { it.startTime.date }
                    .flatMap { (_, dayEvents) ->
                        CollisionDetectionAlgorithm.calculateLayout(dayEvents).entries
                    }
                    .associate { it.key to it.value }
            }
            
            _state.value = currentState.copy(
                events = allTimelineEvents,
                layoutData = layoutData,
                isLoading = false
            )
            
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isLoading = false,
                error = e.message ?: "Neznámá chyba při načítání událostí"
            )
        }
    }
    
    /**
     * Change view mode (day/3-day/week)
     */
    suspend fun setViewMode(mode: ViewMode) {
        if (_state.value.viewMode != mode) {
            _state.value = _state.value.copy(viewMode = mode)
            loadEvents()
        }
    }
    
    /**
     * Navigate to a specific date
     */
    suspend fun setSelectedDate(date: LocalDate) {
        if (_state.value.selectedDate != date) {
            _state.value = _state.value.copy(selectedDate = date)
            loadEvents()
        }
    }
    
    /**
     * Navigate to previous period (day/3-days/week)
     */
    suspend fun navigatePrevious() {
        val currentDate = _state.value.selectedDate
        val newDate = when (_state.value.viewMode) {
            ViewMode.DAY -> currentDate.minus(1, DateTimeUnit.DAY)
            ViewMode.THREE_DAY -> currentDate.minus(3, DateTimeUnit.DAY)
            ViewMode.WEEK -> currentDate.minus(7, DateTimeUnit.DAY)
        }
        setSelectedDate(newDate)
    }
    
    /**
     * Navigate to next period (day/3-days/week)
     */
    suspend fun navigateNext() {
        val currentDate = _state.value.selectedDate
        val newDate = when (_state.value.viewMode) {
            ViewMode.DAY -> currentDate.plus(1, DateTimeUnit.DAY)
            ViewMode.THREE_DAY -> currentDate.plus(3, DateTimeUnit.DAY)
            ViewMode.WEEK -> currentDate.plus(7, DateTimeUnit.DAY)
        }
        setSelectedDate(newDate)
    }
    
    /**
     * Navigate to today
     */
    suspend fun navigateToday() {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        setSelectedDate(today)
    }
    
    /**
     * Toggle between "My events" and "All events"
     */
    suspend fun toggleShowOnlyMine() {
        _state.value = _state.value.copy(
            showOnlyMine = !_state.value.showOnlyMine
        )
        loadEvents()
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
    
    /**
     * Get formatted date label for current view
     */
    fun getDateLabel(): String {
        val currentState = _state.value
        val date = currentState.selectedDate
        
        return when (currentState.viewMode) {
            ViewMode.DAY -> CalendarUtils.formatDate(date)
            ViewMode.THREE_DAY, ViewMode.WEEK -> {
                val timeRange = CalendarUtils.calculateTimeRange(date, currentState.viewMode)
                val startDate = timeRange.start.date
                val endDate = timeRange.end.date
                
                if (startDate.month == endDate.month) {
                    "${startDate.dayOfMonth}–${endDate.dayOfMonth}. ${CalendarUtils.getMonthName(startDate.monthNumber)}"
                } else {
                    "${startDate.dayOfMonth}. ${CalendarUtils.getMonthName(startDate.monthNumber)} – " +
                    "${endDate.dayOfMonth}. ${CalendarUtils.getMonthName(endDate.monthNumber)}"
                }
            }
        }
    }
    
    /**
     * Get list of dates for multi-day views
     */
    fun getDatesInRange(): List<LocalDate> {
        val currentState = _state.value
        val timeRange = CalendarUtils.calculateTimeRange(
            currentState.selectedDate,
            currentState.viewMode
        )
        
        val dates = mutableListOf<LocalDate>()
        var currentDate = timeRange.start.date
        
        while (currentDate <= timeRange.end.date) {
            dates.add(currentDate)
            currentDate = currentDate.plus(1, DateTimeUnit.DAY)
        }
        
        return dates
    }
    
    /**
     * Get events for a specific date (used in multi-day views)
     */
    fun getEventsForDate(date: LocalDate): List<EventLayoutData> {
        return _state.value.layoutData.values.filter { 
            it.event.startTime.date == date 
        }
    }
}
