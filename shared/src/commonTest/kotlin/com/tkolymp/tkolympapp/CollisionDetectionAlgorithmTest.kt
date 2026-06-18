package com.tkolymp.tkolympapp

import com.tkolymp.shared.calendar.CollisionDetectionAlgorithm
import com.tkolymp.shared.calendar.EventLayoutData
import com.tkolymp.shared.calendar.TimelineEvent
import com.tkolymp.shared.event.Event
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollisionDetectionAlgorithmTest {

    private fun minutesToHourMin(minutes: Int) = minutes / 60 to minutes % 60

    private fun makeEvent(id: Long, startMinute: Int, durationMinutes: Int): TimelineEvent {
        val date = LocalDate(2025, 1, 15)
        val (startH, startM) = minutesToHourMin(startMinute)
        val (endH, endM) = minutesToHourMin(startMinute + durationMinutes)
        return TimelineEvent(
            id = id,
            eventId = id,
            title = "Event $id",
            description = null,
            type = "lesson",
            startTime = LocalDateTime(date.year, date.month, date.day, startH, startM),
            endTime = LocalDateTime(date.year, date.month, date.day, endH, endM),
            isCancelled = false,
            isMyEvent = false,
            colorRgb = null,
            event = null
        )
    }

    @Test
    fun `empty list returns empty map`() {
        val result = CollisionDetectionAlgorithm.calculateLayout(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single event gets column 0 and totalColumns 1`() {
        val events = listOf(makeEvent(1L, 10 * 60 + 0, 60))
        val result = CollisionDetectionAlgorithm.calculateLayout(events)
        assertEquals(1, result.size)
        val layout = result[1L]
        assertEquals(0, layout?.column)
        assertEquals(1, layout?.totalColumns)
    }

    @Test
    fun `two non-overlapping events each get full width`() {
        val events = listOf(
            makeEvent(1L, 8 * 60 + 0, 60),
            makeEvent(2L, 10 * 60 + 0, 60)
        )
        val result = CollisionDetectionAlgorithm.calculateLayout(events)
        assertEquals(2, result.size)
        assertEquals(0, result[1L]?.column)
        assertEquals(1, result[1L]?.totalColumns)
        assertEquals(0, result[2L]?.column)
        assertEquals(1, result[2L]?.totalColumns)
    }

    @Test
    fun `two overlapping events share columns`() {
        val events = listOf(
            makeEvent(1L, 10 * 60 + 0, 60),
            makeEvent(2L, 10 * 60 + 30, 60)
        )
        val result = CollisionDetectionAlgorithm.calculateLayout(events)
        assertEquals(2, result.size)
        assertEquals(2, result[1L]?.totalColumns)
        assertEquals(2, result[2L]?.totalColumns)
        assertTrue(result[1L]?.column != result[2L]?.column)
    }

    @Test
    fun `three overlapping events distributed across columns`() {
        val events = listOf(
            makeEvent(1L, 10 * 60 + 0, 120),
            makeEvent(2L, 10 * 60 + 30, 90),
            makeEvent(3L, 11 * 60 + 0, 60)
        )
        val result = CollisionDetectionAlgorithm.calculateLayout(events)
        assertEquals(3, result.size)
        assertEquals(3, result[1L]?.totalColumns)
    }

    @Test
    fun `sequential non-overlapping groups in single collision group`() {
        val events = listOf(
            makeEvent(1L, 8 * 60 + 0, 60),
            makeEvent(2L, 9 * 60 + 0, 60),
            makeEvent(3L, 10 * 60 + 0, 60)
        )
        val result = CollisionDetectionAlgorithm.calculateLayout(events)
        assertEquals(3, result.size)
        result.values.forEach { layout ->
            assertEquals(1, layout.totalColumns)
            assertEquals(0, layout.column)
        }
    }

    @Test
    fun `event that spans entire range gets column 0`() {
        val events = listOf(
            makeEvent(1L, 9 * 60 + 0, 180),
            makeEvent(2L, 10 * 60 + 0, 60),
            makeEvent(3L, 11 * 60 + 0, 60)
        )
        val result = CollisionDetectionAlgorithm.calculateLayout(events)
        assertEquals(3, result.size)
        assertEquals(0, result[1L]?.column)
        // Events 2 and 3 don't overlap each other, so they share column 1
        assertEquals(2, result[1L]?.totalColumns)
        assertEquals(1, result[2L]?.column)
        assertEquals(1, result[3L]?.column)
    }

    @Test
    fun `layout data contains correct start minutes and durations`() {
        val events = listOf(
            makeEvent(1L, 8 * 60 + 30, 90)
        )
        val result = CollisionDetectionAlgorithm.calculateLayout(events)
        val layout = result[1L]
        assertEquals(510, layout?.startMinute)
        assertEquals(90, layout?.durationMinutes)
    }

    @Test
    fun `disjoint collision groups each have independent column counts`() {
        val events = listOf(
            makeEvent(1L, 8 * 60 + 0, 60),
            makeEvent(2L, 8 * 60 + 30, 60),
            makeEvent(3L, 10 * 60 + 0, 60),
            makeEvent(4L, 10 * 60 + 30, 60)
        )
        val result = CollisionDetectionAlgorithm.calculateLayout(events)
        assertEquals(4, result.size)
        assertEquals(2, result[1L]?.totalColumns)
        assertEquals(2, result[2L]?.totalColumns)
        assertEquals(2, result[3L]?.totalColumns)
        assertEquals(2, result[4L]?.totalColumns)
    }
}
