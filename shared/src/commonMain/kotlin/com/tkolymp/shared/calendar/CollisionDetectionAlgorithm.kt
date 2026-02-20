package com.tkolymp.shared.calendar

import kotlinx.datetime.LocalDateTime

/**
 * Algorithm for detecting overlapping events and calculating their layout positions
 * This implements a column-based layout algorithm similar to Google Calendar
 */
object CollisionDetectionAlgorithm {
    
    /**
     * Calculate layout data for a list of events on the same day
     * Returns a map of event ID to layout data with column and width information
     */
    fun calculateLayout(events: List<TimelineEvent>): Map<Long, EventLayoutData> {
        if (events.isEmpty()) return emptyMap()
        
        // Sort events by start time, then by duration (longer first)
        val sortedEvents = events.sortedWith(
            compareBy<TimelineEvent> { it.startTime }
                .thenByDescending { it.endTime.toEpochSeconds() - it.startTime.toEpochSeconds() }
        )
        
        // Group overlapping events into collision groups
        val collisionGroups = findCollisionGroups(sortedEvents)
        
        // Calculate layout for each group
        val layoutMap = mutableMapOf<Long, EventLayoutData>()
        
        for (group in collisionGroups) {
            val groupLayout = layoutCollisionGroup(group.events)
            layoutMap.putAll(groupLayout)
        }
        
        return layoutMap
    }
    
    /**
     * Find groups of overlapping events
     */
    private fun findCollisionGroups(events: List<TimelineEvent>): List<CollisionGroup> {
        if (events.isEmpty()) return emptyList()
        
        val groups = mutableListOf<CollisionGroup>()
        var currentGroup = mutableListOf<TimelineEvent>()
        var groupEndTime: LocalDateTime? = null
        
        for (event in events) {
            if (groupEndTime == null || event.startTime < groupEndTime) {
                // Event overlaps with current group
                currentGroup.add(event)
                groupEndTime = maxOf(groupEndTime ?: event.endTime, event.endTime)
            } else {
                // Start new group
                if (currentGroup.isNotEmpty()) {
                    groups.add(CollisionGroup(currentGroup.toList()))
                }
                currentGroup = mutableListOf(event)
                groupEndTime = event.endTime
            }
        }
        
        // Add last group
        if (currentGroup.isNotEmpty()) {
            groups.add(CollisionGroup(currentGroup.toList()))
        }
        
        return groups
    }
    
    /**
     * Layout a single collision group
     * Uses a greedy column assignment algorithm
     */
    private fun layoutCollisionGroup(events: List<TimelineEvent>): Map<Long, EventLayoutData> {
        if (events.isEmpty()) return emptyMap()
        
        // Track which columns are occupied at each time
        val columns = mutableListOf<MutableList<TimelineEvent>>()
        val eventToColumn = mutableMapOf<Long, Int>()
        
        // Sort by start time for processing
        val sortedEvents = events.sortedBy { it.startTime }
        
        for (event in sortedEvents) {
            // Find the first available column for this event
            var columnIndex = -1
            
            for (i in columns.indices) {
                val column = columns[i]
                // Check if this column is available (no overlapping events)
                val hasOverlap = column.any { it.endTime > event.startTime }
                if (!hasOverlap) {
                    columnIndex = i
                    break
                }
            }
            
            // If no column available, create new one
            if (columnIndex == -1) {
                columnIndex = columns.size
                columns.add(mutableListOf())
            }
            
            columns[columnIndex].add(event)
            eventToColumn[event.id] = columnIndex
        }
        
        val totalColumns = columns.size
        
        // Create layout data for each event
        val layoutMap = mutableMapOf<Long, EventLayoutData>()
        
        for (event in events) {
            val column = eventToColumn[event.id] ?: 0
            val startMinute = CalendarUtils.getMinutesFromDayStart(event.startTime)
            val endMinute = CalendarUtils.getMinutesFromDayStart(event.endTime)
            val durationMinutes = endMinute - startMinute
            
            layoutMap[event.id] = EventLayoutData(
                event = event,
                column = column,
                totalColumns = totalColumns,
                startMinute = startMinute,
                durationMinutes = durationMinutes
            )
        }
        
        return layoutMap
    }
    
    /**
     * Helper to convert LocalDateTime to seconds for comparison
     */
    private fun LocalDateTime.toEpochSeconds(): Long {
        return this.date.toEpochDays() * 86400L + 
               this.hour * 3600L + 
               this.minute * 60L + 
               this.second
    }
}
