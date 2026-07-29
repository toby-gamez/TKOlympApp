package com.tkolymp.tkolympapp

import com.tkolymp.shared.campschedule.NameOccurrence
import com.tkolymp.shared.campschedule.ScheduleDay
import com.tkolymp.shared.campschedule.ScheduleEntry
import com.tkolymp.shared.campschedule.availableGroupNumbers
import com.tkolymp.shared.campschedule.blockGroupNumbers
import com.tkolymp.shared.campschedule.buildJson
import com.tkolymp.shared.campschedule.findNameOccurrences
import com.tkolymp.shared.campschedule.parseScheduleDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ScheduleJsonBuilderTest {

    private val columns = listOf("Filip", "Jana", "Hojdy")

    @Test
    fun `merged row becomes a note entry`() {
        val cells = listOf(
            listOf("8:30", "Prezence ve vestibulu ubytovny")
        )
        val day = parseScheduleDay(buildJson("PONDĚLÍ", columns, cells))
        val entry = day.schedule.single()
        assertIs<ScheduleEntry.Note>(entry)
        assertEquals("8:30", entry.time)
        assertEquals("Prezence ve vestibulu ubytovny", entry.text)
    }

    @Test
    fun `normal row becomes a lesson entry with block extracted from cell text`() {
        val cells = listOf(
            listOf("10:15", "Čížek", "Lenfeld", null)
        )
        val day = parseScheduleDay(buildJson("PONDĚLÍ", columns, cells))
        val entry = day.schedule.single()
        assertIs<ScheduleEntry.Lesson>(entry)
        assertEquals("10:15", entry.time)
        assertEquals(mapOf("Filip" to "Čížek", "Jana" to "Lenfeld", "Hojdy" to null), entry.entries)
    }

    @Test
    fun `block regex extracts LAT or STT plus number and ignores trailing text`() {
        val cells = listOf(
            listOf("11:00", "STT 2 (sál)", null, null)
        )
        val day = parseScheduleDay(buildJson("PONDĚLÍ", columns, cells))
        val entry = day.schedule.single()
        assertIs<ScheduleEntry.Lesson>(entry)
        assertEquals("STT 2", entry.block)
        // Raw OCR text must be preserved verbatim in entries, not "corrected".
        assertEquals("STT 2 (sál)", entry.entries["Filip"])
    }

    @Test
    fun `lesson row with no block match keeps block null`() {
        val cells = listOf(listOf("12:15", "Novák", null, "Chytilová"))
        val day = parseScheduleDay(buildJson("PONDĚLÍ", columns, cells))
        val entry = day.schedule.single()
        assertIs<ScheduleEntry.Lesson>(entry)
        assertNull(entry.block)
    }

    @Test
    fun `block regex preserves multiple group numbers like STT 2 a 3`() {
        val cells = listOf(listOf("11:45", "STT 2 a 3 (sál)", null, null))
        val day = parseScheduleDay(buildJson("PONDĚLÍ", columns, cells))
        val entry = day.schedule.single()
        assertIs<ScheduleEntry.Lesson>(entry)
        assertEquals("STT 2 a 3", entry.block)
    }

    @Test
    fun `blockGroupNumbers extracts every number from a multi-group block`() {
        assertEquals(setOf(2, 3), blockGroupNumbers("STT 2 a 3"))
        assertEquals(setOf(1), blockGroupNumbers("LAT 1"))
    }

    @Test
    fun `blank or duplicate column headers do not silently collide in entries`() {
        val messyColumns = listOf("", "Filip", "")
        val cells = listOf(listOf("8:30", "Anička", "Jaro", "Mirek"))
        val day = parseScheduleDay(buildJson("PONDĚLÍ", messyColumns, cells))

        // Every column keeps its own distinct key, so no data is lost to overwrites.
        assertEquals(3, day.columns.distinct().size)
        val entry = day.schedule.single()
        assertIs<ScheduleEntry.Lesson>(entry)
        assertEquals(3, entry.entries.size)
        assertEquals(listOf("Anička", "Jaro", "Mirek"), day.columns.map { entry.entries[it] })
    }

    @Test
    fun `availableGroupNumbers collects distinct numbers across the whole day`() {
        val day = ScheduleDay(
            day = "PONDĚLÍ",
            columns = columns,
            schedule = listOf(
                ScheduleEntry.Lesson("9:15", "LAT 1", mapOf("Filip" to null, "Jana" to null, "Hojdy" to null)),
                ScheduleEntry.Lesson("10:15", "STT 2 a 3", mapOf("Filip" to null, "Jana" to null, "Hojdy" to null)),
                ScheduleEntry.Lesson("11:15", "LAT 1", mapOf("Filip" to null, "Jana" to null, "Hojdy" to null))
            )
        )
        assertEquals(listOf(1, 2, 3), availableGroupNumbers(day))
    }

    @Test
    fun `findNameOccurrences returns all times and columns across the day`() {
        val day = ScheduleDay(
            day = "PONDĚLÍ",
            columns = columns,
            schedule = listOf(
                ScheduleEntry.Lesson("10:15", "STT 2", mapOf("Filip" to "Novák", "Jana" to "Lenfeld", "Hojdy" to null)),
                ScheduleEntry.Note("12:00", "Oběd"),
                ScheduleEntry.Lesson("15:00", null, mapOf("Filip" to null, "Jana" to "Novák", "Hojdy" to "Chytilová"))
            )
        )

        val hits = findNameOccurrences(day, "Novák")

        assertEquals(
            listOf(
                NameOccurrence("10:15", "Filip", "STT 2"),
                NameOccurrence("15:00", "Jana", null)
            ),
            hits
        )
    }

    @Test
    fun `findNameOccurrences is case-insensitive and trims whitespace`() {
        val day = ScheduleDay(
            day = "PONDĚLÍ",
            columns = columns,
            schedule = listOf(
                ScheduleEntry.Lesson("9:00", "LAT 1", mapOf("Filip" to " novák ", "Jana" to null, "Hojdy" to null))
            )
        )

        val hits = findNameOccurrences(day, "Novák")

        assertEquals(listOf(NameOccurrence("9:00", "Filip", "LAT 1")), hits)
    }

    @Test
    fun `findNameOccurrences returns empty list when name not present`() {
        val day = ScheduleDay(
            day = "PONDĚLÍ",
            columns = columns,
            schedule = listOf(
                ScheduleEntry.Lesson("9:00", "LAT 1", mapOf("Filip" to "Svoboda", "Jana" to null, "Hojdy" to null))
            )
        )

        assertEquals(emptyList(), findNameOccurrences(day, "Novák"))
    }
}
