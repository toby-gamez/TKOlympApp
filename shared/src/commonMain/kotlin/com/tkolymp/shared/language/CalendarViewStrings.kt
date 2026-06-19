package com.tkolymp.shared.language

data class CalendarViewStrings(
    val next: String,
    val previous: String,
    val viewModeDay: String,
    val viewModeThreeDays: String,
    val viewModeWeek: String,
    val freeLesson: String,
    val calendarOptionsTitle: String = "View",
    val weekDayAbbreviations: List<String> = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"),
    val resetToToday: String = "Reset to today",
    val clearFilters: String = "Clear filters",
    val emptyCalendar: String = "No events this week",
)
