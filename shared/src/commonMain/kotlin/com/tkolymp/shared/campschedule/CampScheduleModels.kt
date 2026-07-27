package com.tkolymp.shared.campschedule

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ScheduleEntry {
    abstract val time: String

    @Serializable
    @SerialName("note")
    data class Note(
        override val time: String,
        val text: String?
    ) : ScheduleEntry()

    @Serializable
    @SerialName("lesson")
    data class Lesson(
        override val time: String,
        val block: String?,
        val entries: Map<String, String?>
    ) : ScheduleEntry()
}

@Serializable
data class ScheduleDay(
    val day: String,
    val columns: List<String>,
    val schedule: List<ScheduleEntry>
)
