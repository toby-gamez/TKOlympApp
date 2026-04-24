package com.tkolymp.shared.personalevents

import kotlinx.serialization.Serializable

@Serializable
enum class TrainingType { GENERAL, STT, LAT }

@Serializable
data class PersonalEvent(
    val id: String,
    val title: String,
    val description: String? = null,
    val startIso: String,
    val endIso: String,
    val type: TrainingType = TrainingType.GENERAL,
    // weekly recurrence: day of week (1=Monday .. 7=Sunday)
    val recurrenceDayOfWeek: Int? = null,
    // recurrence interval (optional) - ISO instants representing first/last allowed occurrence
    val recurrenceStartIso: String? = null,
    val recurrenceEndIso: String? = null,
    val location: String? = null,
    val colorHex: String? = null,
    val reminderMinutesBefore: List<Int> = emptyList()
)
