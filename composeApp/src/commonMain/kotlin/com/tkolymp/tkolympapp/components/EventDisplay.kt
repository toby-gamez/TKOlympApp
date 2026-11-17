package com.tkolymp.tkolympapp.components

import androidx.compose.ui.graphics.Color
import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.event.firstTrainerOrEmpty
import com.tkolymp.shared.utils.standardDurationMinutes
import com.tkolymp.shared.utils.parseToLocal
import com.tkolymp.shared.utils.getLocalizedMonthName
import com.tkolymp.shared.language.AppStrings
import kotlinx.datetime.number

/** Display data shared by every list that shows an [EventInstance] as a row (Overview, Calendar). */
internal class TimelineInfo(val title: String, val subtitle: String, val isMine: Boolean, val isFree: Boolean, val cohortColor: Color?)

internal fun isRegisteredBy(inst: EventInstance, myPersonId: String?, myCoupleIds: List<String>): Boolean =
    inst.event?.eventRegistrationsList.orEmpty().any { r ->
        (myPersonId != null && r.person?.id?.toString() == myPersonId) || (r.couple?.id?.toString()?.let { it in myCoupleIds } == true)
    }

internal fun timelineInfo(inst: EventInstance, myPersonId: String?, myCoupleIds: List<String>): TimelineInfo {
    val event = inst.event
    val isLesson = event?.type.equals("lesson", ignoreCase = true)
    val names = event?.eventRegistrationsList.orEmpty().filter { it.parentRegistrationId == null }.mapNotNull { r ->
        r.person?.name ?: r.couple?.let { c ->
            val man = c.man?.let { it.lastName?.takeIf(String::isNotBlank) ?: it.firstName }
            val woman = c.woman?.let { it.lastName?.takeIf(String::isNotBlank) ?: it.firstName }
            listOfNotNull(man?.takeIf(String::isNotBlank), woman?.takeIf(String::isNotBlank)).joinToString(" - ").ifBlank { null }
        }
    }
    val trainer = event.firstTrainerOrEmpty()
    val place = event?.locationText?.takeIf { it.isNotBlank() } ?: event?.location?.name.orEmpty()
    val duration = standardDurationMinutes(inst.since, inst.until)?.let { "$it min" }
    val cohort = if (isLesson) null else event?.eventTargetCohortsList.orEmpty().firstNotNullOfOrNull { tc ->
        tc.cohort?.colorRgb?.takeIf { it.isNotBlank() }?.let { hex -> try { parseColorOrDefault(hex) } catch (_: Exception) { null } }
    }
    val title = if (isLesson) names.joinToString(", ").ifBlank { trainer } else event?.name.orEmpty()
    val subtitle = (if (isLesson) listOf(trainer, place, duration) else listOf(place, trainer, duration))
        .filter { !it.isNullOrBlank() }.joinToString(" · ")
    return TimelineInfo(
        title = title,
        subtitle = subtitle,
        isMine = isRegisteredBy(inst, myPersonId, myCoupleIds),
        isFree = isLesson && names.isEmpty() && !inst.isCancelled,
        cohortColor = cohort
    )
}


internal fun campDateRange(since: String?, until: String?): String {
    val a = parseToLocal(since) ?: return ""
    val b = parseToLocal(until)
    return if (b == null || b.date == a.date) "${a.day}." else "${a.day}.–${b.day}."
}

internal fun campMonth(since: String?): String {
    val a = parseToLocal(since) ?: return ""
    return getLocalizedMonthName(a.month.number, AppStrings.currentLanguage.code)
}


/** Right-hand status of a camp card: "in 19 days", "running" or "past". */
internal fun campWhenLabel(inst: EventInstance, today: kotlinx.datetime.LocalDate): String? {
    val strings = com.tkolymp.shared.language.AppStrings.current
    val start = parseToLocal(inst.since)?.date ?: return null
    val end = parseToLocal(inst.until)?.date ?: start
    return when {
        end < today -> strings.events.pastLabel
        start <= today -> strings.events.runningLabel
        else -> strings.overview.inDays.replace("%d", (start.toEpochDays() - today.toEpochDays()).toString())
    }
}
