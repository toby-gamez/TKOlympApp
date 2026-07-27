package com.tkolymp.tkolympapp

import com.tkolymp.shared.campschedule.campDates
import com.tkolymp.shared.campschedule.dayIndex
import com.tkolymp.shared.campschedule.isRozpisTabVisible
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CampDayCalculatorTest {

    private fun instance(since: String, until: String) = buildJsonObject {
        put("since", JsonPrimitive(since))
        put("until", JsonPrimitive(until))
    }

    @Test
    fun `campDates spans from earliest since to latest until inclusive`() {
        val instances = listOf(
            instance("2026-08-03T09:00:00Z", "2026-08-03T18:00:00Z"),
            instance("2026-08-04T09:00:00Z", "2026-08-04T18:00:00Z"),
            instance("2026-08-05T09:00:00Z", "2026-08-05T18:00:00Z")
        )

        val dates = campDates(instances)

        assertEquals(
            listOf(LocalDate(2026, 8, 3), LocalDate(2026, 8, 4), LocalDate(2026, 8, 5)),
            dates
        )
    }

    @Test
    fun `campDates returns empty list when no instances`() {
        assertEquals(emptyList(), campDates(emptyList()))
    }

    @Test
    fun `dayIndex counts zero-based days from camp start`() {
        val start = LocalDate(2026, 8, 3)
        assertEquals(0, dayIndex(start, LocalDate(2026, 8, 3)))
        assertEquals(1, dayIndex(start, LocalDate(2026, 8, 4)))
        assertEquals(2, dayIndex(start, LocalDate(2026, 8, 5)))
    }

    @Test
    fun `Rozpis tab is visible the day before a CAMP starts and every day after`() {
        val campStartIso = "2026-08-03T09:00:00Z"
        assertFalse(isRozpisTabVisible("CAMP", campStartIso, LocalDate(2026, 8, 1)))
        assertTrue(isRozpisTabVisible("CAMP", campStartIso, LocalDate(2026, 8, 2)))
        assertTrue(isRozpisTabVisible("CAMP", campStartIso, LocalDate(2026, 8, 3)))
        assertTrue(isRozpisTabVisible("CAMP", campStartIso, LocalDate(2026, 8, 10)))
    }

    @Test
    fun `Rozpis tab is never visible for non-CAMP events`() {
        assertFalse(isRozpisTabVisible("lesson", "2026-08-03T09:00:00Z", LocalDate(2026, 8, 3)))
        assertFalse(isRozpisTabVisible("GROUP", "2026-08-03T09:00:00Z", LocalDate(2026, 8, 3)))
    }

    @Test
    fun `Rozpis tab is not visible when campStartIso is missing`() {
        assertFalse(isRozpisTabVisible("CAMP", null, LocalDate(2026, 8, 3)))
    }
}
