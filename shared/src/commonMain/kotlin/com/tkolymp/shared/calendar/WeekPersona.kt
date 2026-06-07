package com.tkolymp.shared.calendar

import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.event.EventType
import com.tkolymp.shared.event.toEventType


data class DailyDensity(
    val count: Int
)

data class WeekVibesData(
    val persona: WeekPersona,
    val dailyDensity: List<DailyDensity>,
    val maxDensity: Int,
    val dayOfWeekOrdinals: List<Int>
)

enum class WeekPersona(
    val emoji: String,
    val labelKey: String
) {
    HUSTLE("\uD83C\uDFCB\uFE0F", "hustle"),
    EASY("\uD83E\uDDD8", "easy"),
    SPRINT("\u26A1", "sprint"),
    MIX("\uD83C\uDFAD", "mix"),
    SOCIAL("\uD83D\uDC65", "social"),
    CAMP("\uD83C\uDFD5\uFE0F", "camp"),
    ALL_ROUNDER("\u2B50", "allRounder");
}

fun computeWeekVibes(
    lessonsByTrainerByDay: Map<String, Map<String, List<EventInstance>>>,
    otherEventsByDay: Map<String, List<EventInstance>>,
    visibleDates: List<String>
): WeekVibesData {
    val dailyDensity = visibleDates.map { date ->
        val dayCount = (lessonsByTrainerByDay[date]?.values?.sumOf { it.size } ?: 0) +
            (otherEventsByDay[date]?.size ?: 0)
        DailyDensity(dayCount)
    }

    val dayOfWeekOrdinals = visibleDates.map { date ->
        try { kotlinx.datetime.LocalDate.parse(date).dayOfWeek.ordinal } catch (_: Exception) { 0 }
    }

    val maxDensity = dailyDensity.maxOfOrNull { it.count } ?: 0

    val totalEvents = dailyDensity.sumOf { it.count }
    val daysWithEvents = dailyDensity.count { it.count > 0 }

    val uniqueTrainers = lessonsByTrainerByDay.values
        .flatMap { day -> day.keys }
        .filter { it.isNotBlank() }
        .toSortedSet()

    val eventTypes = mutableSetOf<String>()
    for ((_, lessons) in lessonsByTrainerByDay) {
        for ((_, instances) in lessons) {
            for (inst in instances) {
                inst.event?.type?.let { eventTypes.add(it) }
            }
        }
    }
    for ((_, events) in otherEventsByDay) {
        for (inst in events) {
            inst.event?.type?.let { eventTypes.add(it) }
        }
    }

    val typeCount = eventTypes.mapNotNull { it.toEventType() }.distinct().size
    val hasCamp = eventTypes.any {
        it.equals(EventType.CAMP.rawValue, ignoreCase = true)
    }

    val persona = when {
        hasCamp -> WeekPersona.CAMP
        totalEvents >= 8 -> WeekPersona.HUSTLE
        totalEvents <= 2 -> WeekPersona.EASY
        daysWithEvents <= 2 && totalEvents >= 3 -> WeekPersona.SPRINT
        uniqueTrainers.size >= 3 -> WeekPersona.SOCIAL
        typeCount >= 3 -> WeekPersona.MIX
        else -> WeekPersona.ALL_ROUNDER
    }

    val sorted = dailyDensity.zip(dayOfWeekOrdinals).sortedBy { it.second }
    return WeekVibesData(
        persona = persona,
        dailyDensity = sorted.map { it.first },
        maxDensity = maxDensity,
        dayOfWeekOrdinals = sorted.map { it.second }
    )
}
