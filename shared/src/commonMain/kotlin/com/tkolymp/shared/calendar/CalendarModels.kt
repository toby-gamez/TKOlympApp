package com.tkolymp.shared.calendar

import com.tkolymp.shared.event.BigInt
import com.tkolymp.shared.event.Event
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Represents a single event instance with additional timeline layout data
 */
data class TimelineEvent(
    val id: BigInt,
    val eventId: BigInt?,
    val title: String,
    val description: String?,
    val type: String?,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val isCancelled: Boolean,
    val isMyEvent: Boolean,  // true if user is registered or is a trainer
    val colorRgb: String?,   // from cohort or default
    val event: Event?
)

/**
 * Layout data for positioning an event in the timeline grid
 * Used for collision detection and positioning
 */
data class EventLayoutData(
    val event: TimelineEvent,
    val column: Int,        // which column (0-based)
    val totalColumns: Int,  // total number of overlapping columns
    val startMinute: Int,   // minutes from day start
    val durationMinutes: Int
)

/**
 * Group of overlapping events that need to be laid out side-by-side
 */
data class CollisionGroup(
    val events: List<TimelineEvent>
)

/**
 * State for the calendar view
 */
data class CalendarViewState(
    val viewMode: ViewMode = ViewMode.DAY,
    val selectedDate: LocalDate,
    val events: List<TimelineEvent> = emptyList(),
    val layoutData: Map<BigInt, EventLayoutData> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showOnlyMine: Boolean = false
)

/**
 * View mode for calendar
 */
enum class ViewMode(val days: Int) {
    DAY(1),
    THREE_DAY(3),
    WEEK(7)
}

/**
 * Time range for filtering events
 */
data class TimeRange(
    val start: LocalDateTime,
    val end: LocalDateTime
)
