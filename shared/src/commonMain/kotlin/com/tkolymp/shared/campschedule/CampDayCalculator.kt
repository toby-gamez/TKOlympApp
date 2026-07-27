package com.tkolymp.shared.campschedule

import com.tkolymp.shared.event.EventType
import com.tkolymp.shared.event.toEventType
import com.tkolymp.shared.utils.str
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.json.JsonObject

/**
 * Returns every calendar date the camp spans, inclusive, derived from the earliest
 * `since` and latest `until` across the event's instances (one per camp day).
 */
fun campDates(instances: List<JsonObject>): List<LocalDate> {
    val sinceDates = instances.mapNotNull { it.str("since")?.take(10) }.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
    val untilDates = instances.mapNotNull { it.str("until")?.take(10) }.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
    val start = sinceDates.minOrNull() ?: return emptyList()
    val end = untilDates.maxOrNull() ?: start

    val dates = mutableListOf<LocalDate>()
    var current = start
    while (current <= end) {
        dates += current
        current = current.plus(DatePeriod(days = 1))
    }
    return dates
}

/** Zero-based index of [date] within the camp, counted from [campStart]. */
fun dayIndex(campStart: LocalDate, date: LocalDate): Int =
    (date.toEpochDays() - campStart.toEpochDays()).toInt()

/**
 * The Rozpis tab is only shown for CAMP events, starting the day before the camp
 * begins (so participants can check the schedule the evening prior).
 */
fun isRozpisTabVisible(eventType: String, campStartIso: String?, today: LocalDate): Boolean {
    if (eventType.toEventType() != EventType.CAMP) return false
    val campStart = campStartIso?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
    return today >= campStart.minus(DatePeriod(days = 1))
}
