package com.tkolymp.tkolympapp

import com.tkolymp.shared.campschedule.Gender
import com.tkolymp.shared.campschedule.ScheduleDay
import com.tkolymp.shared.campschedule.ScheduleEntry
import com.tkolymp.shared.campschedule.computeMySchedule
import com.tkolymp.shared.campschedule.effectiveSearchNames
import com.tkolymp.shared.campschedule.myReminderTargets
import com.tkolymp.shared.campschedule.myMatchedColumn
import com.tkolymp.shared.campschedule.resolveMyTableName
import com.tkolymp.shared.campschedule.resolvePartnerName
import com.tkolymp.shared.people.ActiveCouple
import com.tkolymp.shared.people.CoupleMember
import com.tkolymp.shared.people.PersonDetails
import kotlin.test.Test
import kotlin.test.assertEquals

class MyScheduleFilterTest {

    private fun person(
        firstName: String?,
        lastName: String?,
        activeCouplesList: List<ActiveCouple> = emptyList()
    ) = PersonDetails(
        id = "1",
        firstName = firstName,
        lastName = lastName,
        birthDate = null,
        cstsId = null,
        email = null,
        gender = null,
        isTrainer = false,
        phone = null,
        wdsfId = null,
        activeCouplesList = activeCouplesList,
        cohortMembershipsList = emptyList()
    )

    private val day = ScheduleDay(
        day = "PONDĚLÍ",
        columns = listOf("Filip", "Jana", "Hojdy"),
        schedule = listOf(
            ScheduleEntry.Note("8:30", "Prezence ve vestibulu ubytovny"),
            ScheduleEntry.Lesson("10:15", "STT 2", mapOf("Filip" to "Novák", "Jana" to "Lenfeld", "Hojdy" to null)),
            ScheduleEntry.Note("12:00", "Oběd"),
            ScheduleEntry.Lesson("15:00", "LAT 1", mapOf("Filip" to null, "Jana" to "Svoboda", "Hojdy" to "Chytilová"))
        )
    )

    @Test
    fun `resolveMyTableName uses the couple's man surname when in an active couple`() {
        val me = person(
            firstName = "Anna",
            lastName = "Chytilová",
            activeCouplesList = listOf(ActiveCouple(id = "c1", man = CoupleMember("Petr", "Novák"), woman = CoupleMember("Anna", "Chytilová")))
        )
        assertEquals("Novák", resolveMyTableName(me))
    }

    @Test
    fun `resolveMyTableName falls back to own first name when solo`() {
        val me = person(firstName = "Jana", lastName = "Lenfeldová")
        assertEquals("Jana", resolveMyTableName(me))
    }

    @Test
    fun `computeMySchedule includes note rows that have text`() {
        val mine = computeMySchedule(day, myTableName = "Nobody", myGroupNumber = null)
        assertEquals(listOf("8:30", "12:00"), mine.filterIsInstance<ScheduleEntry.Note>().map { it.time })
    }

    @Test
    fun `computeMySchedule drops note rows with blank text`() {
        val dayWithBlankNote = day.copy(schedule = day.schedule + ScheduleEntry.Note("18:00", null))
        val mine = computeMySchedule(dayWithBlankNote, myTableName = "Nobody", myGroupNumber = null)
        assertEquals(listOf("8:30", "12:00"), mine.filterIsInstance<ScheduleEntry.Note>().map { it.time })
    }

    @Test
    fun `computeMySchedule includes lessons matching my group number`() {
        val mine = computeMySchedule(day, myTableName = "Nobody", myGroupNumber = 2)
        val lessons = mine.filterIsInstance<ScheduleEntry.Lesson>()
        assertEquals(listOf("10:15"), lessons.map { it.time })
    }

    @Test
    fun `computeMySchedule matches a multi-group block like STT 2 a 3 by either number`() {
        val multiGroupDay = day.copy(
            schedule = day.schedule + ScheduleEntry.Lesson("16:00", "STT 2 a 3", mapOf("Filip" to null, "Jana" to null, "Hojdy" to null))
        )
        val mineGroup3 = computeMySchedule(multiGroupDay, myTableName = "Nobody", myGroupNumber = 3)
        assertEquals(listOf("16:00"), mineGroup3.filterIsInstance<ScheduleEntry.Lesson>().map { it.time })
    }

    @Test
    fun `computeMySchedule includes lessons where my table name appears anywhere in entries`() {
        val mine = computeMySchedule(day, myTableName = "Chytilová", myGroupNumber = null)
        val lessons = mine.filterIsInstance<ScheduleEntry.Lesson>()
        assertEquals(listOf("15:00"), lessons.map { it.time })
    }

    @Test
    fun `myReminderTargets includes both matched lessons and note rows`() {
        val targets = myReminderTargets(day, myTableName = "Chytilová", myGroupNumber = 2)
        assertEquals(listOf("8:30", "10:15", "12:00", "15:00"), targets.map { it.time })
    }

    @Test
    fun `resolveMyTableName uses own first name for a woman even when in an active couple`() {
        val me = person(
            firstName = "Anna",
            lastName = "Chytilová",
            activeCouplesList = listOf(ActiveCouple(id = "c1", man = CoupleMember("Petr", "Novák"), woman = CoupleMember("Anna", "Chytilová")))
        )
        assertEquals("Anna", resolveMyTableName(me, Gender.FEMALE))
    }

    @Test
    fun `resolvePartnerName returns the couple's man surname only for a woman in an active couple`() {
        val me = person(
            firstName = "Anna",
            lastName = "Chytilová",
            activeCouplesList = listOf(ActiveCouple(id = "c1", man = CoupleMember("Petr", "Novák"), woman = CoupleMember("Anna", "Chytilová")))
        )
        assertEquals("Novák", resolvePartnerName(me, Gender.FEMALE))
        assertEquals("", resolvePartnerName(me, Gender.MALE))
        assertEquals("", resolvePartnerName(me, null))
    }

    @Test
    fun `computeMySchedule also matches lessons via the partner name`() {
        val mine = computeMySchedule(day, myTableName = "Anna", myGroupNumber = null, partnerName = "Novák")
        val lessons = mine.filterIsInstance<ScheduleEntry.Lesson>()
        assertEquals(listOf("10:15"), lessons.map { it.time })
    }

    @Test
    fun `myMatchedColumn finds the partner name when my own name doesn't match`() {
        val lesson = day.schedule.filterIsInstance<ScheduleEntry.Lesson>().first { it.time == "10:15" }
        assertEquals("Filip", myMatchedColumn(lesson, myTableName = "Anna", partnerName = "Novák"))
    }

    @Test
    fun `effectiveSearchNames always uses just his own name for a man`() {
        assertEquals("Petr" to "", effectiveSearchNames(Gender.MALE, "Petr", "Novák"))
        assertEquals("Petr" to "", effectiveSearchNames(null, "Petr", "Novák"))
    }

    @Test
    fun `effectiveSearchNames uses only the partner's name for a woman who has one`() {
        assertEquals("" to "Novák", effectiveSearchNames(Gender.FEMALE, "Anna", "Novák"))
    }

    @Test
    fun `effectiveSearchNames falls back to her own name for a woman without a partner`() {
        assertEquals("Anna" to "", effectiveSearchNames(Gender.FEMALE, "Anna", ""))
    }

    @Test
    fun `computeMySchedule matches a name inside a hyphen-joined pair of two people`() {
        val pairedDay = day.copy(
            schedule = day.schedule + ScheduleEntry.Lesson(
                "16:00", null,
                mapOf("Filip" to "Grulichová-Krbečková", "Jana" to null, "Hojdy" to null)
            )
        )
        val mine = computeMySchedule(pairedDay, myTableName = "Krbečková", myGroupNumber = null)
        assertEquals(listOf("16:00"), mine.filterIsInstance<ScheduleEntry.Lesson>().map { it.time })
    }

    @Test
    fun `computeMySchedule matches a name inside a comma-joined pair of two people`() {
        val pairedDay = day.copy(
            schedule = day.schedule + ScheduleEntry.Lesson(
                "16:00", null,
                mapOf("Filip" to "Girl1, Girl2", "Jana" to null, "Hojdy" to null)
            )
        )
        val mine = computeMySchedule(pairedDay, myTableName = "Girl2", myGroupNumber = null)
        assertEquals(listOf("16:00"), mine.filterIsInstance<ScheduleEntry.Lesson>().map { it.time })
    }

    @Test
    fun `myMatchedColumn finds a name inside a hyphen-joined pair`() {
        val lesson = ScheduleEntry.Lesson("16:00", null, mapOf("Filip" to "Grulichová-Krbečková"))
        assertEquals("Filip", myMatchedColumn(lesson, myTableName = "Grulichová"))
    }

    @Test
    fun `computeMySchedule matches a name typed without diacritics`() {
        val mine = computeMySchedule(day, myTableName = "Krizan", myGroupNumber = null)
        assertEquals(emptyList(), mine.filterIsInstance<ScheduleEntry.Lesson>().map { it.time })

        val dayWithDiacritics = day.copy(
            schedule = day.schedule + ScheduleEntry.Lesson("16:00", null, mapOf("Filip" to "Křížan", "Jana" to null, "Hojdy" to null))
        )
        val mineTyped = computeMySchedule(dayWithDiacritics, myTableName = "Krizan", myGroupNumber = null)
        assertEquals(listOf("16:00"), mineTyped.filterIsInstance<ScheduleEntry.Lesson>().map { it.time })

        val mineWrongAccent = computeMySchedule(dayWithDiacritics, myTableName = "Křižan", myGroupNumber = null)
        assertEquals(listOf("16:00"), mineWrongAccent.filterIsInstance<ScheduleEntry.Lesson>().map { it.time })
    }
}
