package com.tkolymp.shared.viewmodels

import com.tkolymp.shared.event.EventInstance
import kotlin.time.Instant

/** True when the registration list of [inst] contains the user, directly or through one of their couples. */
internal fun EventInstance.isRegisteredBy(myPersonId: String?, myCoupleIds: List<String>): Boolean =
    event?.eventRegistrationsList.orEmpty().any { r ->
        (myPersonId != null && r.person?.id?.toString() == myPersonId) ||
            (r.couple?.id?.toString()?.let { it in myCoupleIds } == true)
    }

/**
 * The soonest not-yet-started, non-cancelled instance the user is registered for, or null.
 * Instances whose start can't be parsed are ignored.
 */
fun findNextTraining(
    events: List<EventInstance>,
    myPersonId: String?,
    myCoupleIds: List<String>,
    now: Instant
): EventInstance? = events
    .filter { !it.isCancelled && it.isRegisteredBy(myPersonId, myCoupleIds) }
    .mapNotNull { inst ->
        val start = try { inst.since?.let { Instant.parse(it) } } catch (_: Exception) { null }
        if (start != null && start >= now) start to inst else null
    }
    .minByOrNull { it.first }
    ?.second

/** Whole minutes from [now] until [since], or null when unparsable; never negative. */
fun minutesUntil(since: String?, now: Instant): Int? {
    val start = try { since?.let { Instant.parse(it) } } catch (_: Exception) { null } ?: return null
    return (start - now).inWholeMinutes.toInt().coerceAtLeast(0)
}
