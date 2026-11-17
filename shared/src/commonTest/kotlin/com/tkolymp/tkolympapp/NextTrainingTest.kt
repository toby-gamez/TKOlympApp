package com.tkolymp.tkolympapp

import com.tkolymp.shared.event.Couple
import com.tkolymp.shared.event.Event
import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.event.Person
import com.tkolymp.shared.event.Registration
import com.tkolymp.shared.viewmodels.findNextTraining
import com.tkolymp.shared.viewmodels.minutesUntil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private val NOW = Instant.parse("2026-11-18T13:41:00Z")

private fun inst(
    id: Long,
    since: String?,
    cancelled: Boolean = false,
    personId: Long? = null,
    coupleId: Long? = null
) = EventInstance(
    id = id,
    isCancelled = cancelled,
    since = since,
    until = since,
    event = Event(
        id = id,
        eventRegistrationsList = listOfNotNull(
            personId?.let { Registration(person = Person(id = it)) },
            coupleId?.let { Registration(couple = Couple(id = it)) }
        )
    )
)

class NextTrainingTest {
    @Test
    fun picksSoonestFutureRegisteredInstance() {
        val events = listOf(
            inst(1, "2026-11-18T16:00:00Z", personId = 7),
            inst(2, "2026-11-18T14:15:00Z", personId = 7),
            inst(3, "2026-11-18T14:00:00Z", personId = 99)
        )
        assertEquals(2L, findNextTraining(events, "7", emptyList(), NOW)?.id)
    }

    @Test
    fun matchesThroughCouple() {
        val events = listOf(inst(1, "2026-11-18T15:00:00Z", coupleId = 5))
        assertEquals(1L, findNextTraining(events, null, listOf("5"), NOW)?.id)
    }

    @Test
    fun skipsPastCancelledAndUnparsable() {
        val events = listOf(
            inst(1, "2026-11-18T10:00:00Z", personId = 7),
            inst(2, "2026-11-18T15:00:00Z", cancelled = true, personId = 7),
            inst(3, "not-a-date", personId = 7),
            inst(4, null, personId = 7)
        )
        assertNull(findNextTraining(events, "7", emptyList(), NOW))
    }

    @Test
    fun noIdentityMatchesNothing() {
        assertNull(findNextTraining(listOf(inst(1, "2026-11-18T15:00:00Z", personId = 7)), null, emptyList(), NOW))
    }

    @Test
    fun minutesUntilIsWholeAndNeverNegative() {
        assertEquals(34, minutesUntil("2026-11-18T14:15:00Z", NOW))
        assertEquals(0, minutesUntil("2026-11-18T10:00:00Z", NOW))
        assertNull(minutesUntil("garbage", NOW))
        assertNull(minutesUntil(null, NOW))
    }
}
