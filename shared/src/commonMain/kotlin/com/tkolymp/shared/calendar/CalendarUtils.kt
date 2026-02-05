package com.tkolymp.shared.calendar

import com.tkolymp.shared.event.BigInt
import com.tkolymp.shared.event.Event
import com.tkolymp.shared.event.EventInstance
import kotlinx.datetime.*

/**
 * Utilities for converting EventInstance to TimelineEvent and working with time ranges
 */
object CalendarUtils {
    
    /**
     * Convert EventInstance to TimelineEvent for timeline display
     */
    fun eventInstanceToTimelineEvent(
        instance: EventInstance,
        myPersonId: String?,
        myCoupleIds: List<String>
    ): TimelineEvent? {
        val startTime = instance.since?.let { parseUtcToLocal(it) } ?: return null
        val endTime = instance.until?.let { parseUtcToLocal(it) } ?: return null
        
        val event = instance.event
        
        // Determine title: if event has no name and is a lesson, use trainer name
        val title = when {
            !event?.name.isNullOrBlank() -> event?.name!!
            event?.type?.equals("lesson", ignoreCase = true) == true -> {
                event.eventTrainersList.firstOrNull()?.takeIf { it.isNotBlank() } ?: "Bez názvu"
            }
            else -> "Bez názvu"
        }
        
        // Determine if this is "my" event
        val isMyEvent = isUserParticipant(event, myPersonId, myCoupleIds)
        
        // Get color from cohort or use default
        val colorRgb = getEventColor(event)
        
        return TimelineEvent(
            id = instance.id,
            eventId = event?.id,
            title = title,
            description = event?.description,
            type = event?.type,
            startTime = startTime,
            endTime = endTime,
            isCancelled = instance.isCancelled,
            isMyEvent = isMyEvent,
            colorRgb = colorRgb,
            event = event
        )
    }
    
    /**
     * Parse UTC datetime string to LocalDateTime
     */
    fun parseUtcToLocal(utcString: String): LocalDateTime {
        return try {
            val instant = Instant.parse(utcString)
            instant.toLocalDateTime(TimeZone.currentSystemDefault())
        } catch (e: Exception) {
            // Fallback: try parsing as ISO local datetime
            LocalDateTime.parse(utcString.substringBefore('Z'))
        }
    }
    
    /**
     * Check if user is participant (registered or trainer)
     */
    private fun isUserParticipant(
        event: Event?,
        myPersonId: String?,
        myCoupleIds: List<String>
    ): Boolean {
        if (event == null || myPersonId == null) return false
        
        // Check if user is registered
        val isRegistered = event.eventRegistrationsList.any { reg ->
            reg.person?.id?.toString() == myPersonId ||
            (reg.couple?.id != null && myCoupleIds.contains(reg.couple?.id.toString()))
        }
        
        // Check if user is trainer
        val isTrainer = event.eventTrainersList.any { trainerName ->
            // This is simplified - in real app you'd need trainer ID mapping
            false
        }
        
        return isRegistered || isTrainer
    }
    
    /**
     * Get event color - from cohort or default
     */
    private fun getEventColor(event: Event?): String {
        if (event == null) return "#ADD8E6" // light blue default
        
        // Lessons use secondary theme color (will be handled in UI layer)
        if (event.type?.equals("lesson", ignoreCase = true) == true) {
            return "lesson" // special marker
        }
        
        // Try to get color from first cohort
        val cohortColor = event.eventTargetCohortsList.firstOrNull()?.cohort?.colorRgb
        if (!cohortColor.isNullOrBlank()) {
            return normalizeColorString(cohortColor)
        }
        
        return "#ADD8E6" // light blue default
    }
    
    /**
     * Normalize color string to #RRGGBB format
     */
    private fun normalizeColorString(color: String): String {
        val trimmed = color.trim()
        
        // Already in #RRGGBB format
        if (trimmed.startsWith("#") && trimmed.length == 7) {
            return trimmed
        }
        
        // rgb(r,g,b) format
        if (trimmed.startsWith("rgb(") && trimmed.endsWith(")")) {
            val values = trimmed.substring(4, trimmed.length - 1).split(",")
            if (values.size == 3) {
                try {
                    val r = values[0].trim().toInt()
                    val g = values[1].trim().toInt()
                    val b = values[2].trim().toInt()
                    return "#${r.toString(16).padStart(2, '0')}${g.toString(16).padStart(2, '0')}${b.toString(16).padStart(2, '0')}"
                } catch (e: Exception) {
                    return "#ADD8E6"
                }
            }
        }
        
        // RRGGBB without #
        if (trimmed.length == 6) {
            return "#$trimmed"
        }
        
        return "#ADD8E6"
    }
    
    /**
     * Calculate time range for view mode
     */
    fun calculateTimeRange(date: LocalDate, viewMode: ViewMode): TimeRange {
        val startDate = when (viewMode) {
            ViewMode.DAY -> date
            ViewMode.THREE_DAY -> date
            ViewMode.WEEK -> {
                // Start from Monday
                val dayOfWeek = date.dayOfWeek
                val daysFromMonday = dayOfWeek.ordinal // Monday = 0
                date.minus(daysFromMonday, DateTimeUnit.DAY)
            }
        }
        
        val endDate = startDate.plus(viewMode.days - 1, DateTimeUnit.DAY)
        
        return TimeRange(
            start = startDate.atTime(0, 0),
            end = endDate.atTime(23, 59, 59)
        )
    }
    
    /**
     * Get start of day in minutes (e.g., 480 for 8:00 AM)
     */
    fun getMinutesFromDayStart(time: LocalDateTime): Int {
        return time.hour * 60 + time.minute
    }
    
    /**
     * Format time for display (e.g., "14:30")
     */
    fun formatTime(time: LocalDateTime): String {
        return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
    }
    
    /**
     * Format date for display
     */
    fun formatDate(date: LocalDate, includeYear: Boolean = false): String {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        
        return when (date) {
            today -> "dnes"
            tomorrow -> "zítra"
            else -> {
                if (includeYear || date.year != today.year) {
                    "${date.dayOfMonth}. ${getMonthName(date.monthNumber)} ${date.year}"
                } else {
                    "${date.dayOfMonth}. ${getMonthName(date.monthNumber)}"
                }
            }
        }
    }
    
    /**
     * Get Czech month name
     */
    fun getMonthName(month: Int): String {
        return when (month) {
            1 -> "ledna"
            2 -> "února"
            3 -> "března"
            4 -> "dubna"
            5 -> "května"
            6 -> "června"
            7 -> "července"
            8 -> "srpna"
            9 -> "září"
            10 -> "října"
            11 -> "listopadu"
            12 -> "prosince"
            else -> ""
        }
    }
    
    /**
     * Format day label for multi-day view (e.g., "Po 15.3.")
     */
    fun formatDayLabel(date: LocalDate): String {
        val dayName = when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> "Po"
            DayOfWeek.TUESDAY -> "Út"
            DayOfWeek.WEDNESDAY -> "St"
            DayOfWeek.THURSDAY -> "Čt"
            DayOfWeek.FRIDAY -> "Pá"
            DayOfWeek.SATURDAY -> "So"
            DayOfWeek.SUNDAY -> "Ne"
            else -> ""
        }
        
        return "$dayName ${date.dayOfMonth}.${date.monthNumber}."
    }
}
