package com.tkolymp.tkolympapp

import com.tkolymp.shared.campschedule.ScheduleDay
import com.tkolymp.shared.campschedule.ScheduleEntry
import com.tkolymp.shared.campschedule.computeMySchedule
import com.tkolymp.shared.campschedule.myLessonReminderTargets
import com.tkolymp.shared.campschedule.resolveMyTableName
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
    fun `computeMySchedule always includes note rows`() {
        val mine = computeMySchedule(day, myTableName = "Nobody", myGroupNumber = null)
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
    fun `myLessonReminderTargets excludes note rows`() {
        val targets = myLessonReminderTargets(day, myTableName = "Chytilová", myGroupNumber = 2)
        assertEquals(listOf("10:15", "15:00"), targets.map { it.time })
    }
}
