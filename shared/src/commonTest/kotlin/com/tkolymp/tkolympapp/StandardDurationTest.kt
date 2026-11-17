package com.tkolymp.tkolympapp

import com.tkolymp.shared.utils.standardDurationMinutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StandardDurationTest {
    @Test
    fun fortyFiveAndNinetyAreShown() {
        assertEquals(45, standardDurationMinutes("2026-11-18T15:00:00Z", "2026-11-18T15:45:00Z"))
        assertEquals(90, standardDurationMinutes("2026-11-18T18:00:00Z", "2026-11-18T19:30:00Z"))
    }

    @Test
    fun otherLengthsShowNothing() {
        assertNull(standardDurationMinutes("2026-11-18T15:00:00Z", "2026-11-18T16:00:00Z"))
        assertNull(standardDurationMinutes("2026-11-18T15:00:00Z", "2026-11-18T15:30:00Z"))
    }

    @Test
    fun multiDayEventsShowNothing() {
        assertNull(standardDurationMinutes("2026-12-06T08:00:00Z", "2026-12-08T16:00:00Z"))
    }

    @Test
    fun missingOrBrokenInputShowsNothing() {
        assertNull(standardDurationMinutes(null, "2026-11-18T15:45:00Z"))
        assertNull(standardDurationMinutes("x", "y"))
    }
}

class DaysSinceTest {
    private val today = kotlinx.datetime.LocalDate(2026, 11, 18)

    @Test
    fun sameDayIsZero() = assertEquals(0, com.tkolymp.shared.utils.daysSince("2026-11-18T12:00:00", today))

    @Test
    fun countsWholeDays() = assertEquals(3, com.tkolymp.shared.utils.daysSince("2026-11-15T09:00:00", today))

    @Test
    fun futureAndGarbageAreHandled() {
        assertEquals(0, com.tkolymp.shared.utils.daysSince("2026-11-25T09:00:00", today))
        assertNull(com.tkolymp.shared.utils.daysSince("nope", today))
        assertNull(com.tkolymp.shared.utils.daysSince(null, today))
    }
}
