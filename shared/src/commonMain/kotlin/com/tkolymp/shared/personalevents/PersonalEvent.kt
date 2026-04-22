package com.tkolymp.shared.personalevents

import kotlinx.serialization.Serializable

@Serializable
data class PersonalEvent(
    val id: String,
    val title: String,
    val description: String? = null,
    val startIso: String,
    val endIso: String,
    val location: String? = null,
    val colorHex: String? = null,
    val reminderMinutesBefore: List<Int> = emptyList()
)
