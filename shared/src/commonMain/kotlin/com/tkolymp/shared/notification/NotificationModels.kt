package com.tkolymp.shared.notification

import com.tkolymp.shared.event.Event
import kotlinx.serialization.Serializable

@Serializable
data class NotificationRule(
    val id: String,
    val enabled: Boolean = true,
    val filterType: FilterType = FilterType.ALL,
    val filterValue: String? = null,
    val locations: List<String> = emptyList(),
    val trainers: List<String> = emptyList(),
    val types: List<String> = emptyList(),
    val timesBeforeMinutes: List<Int> = listOf(60, 5)
)

@Serializable
data class NotificationSettings(
    val globalEnabled: Boolean = true,
    val rules: List<NotificationRule> = emptyList()
)

@Serializable
data class ScheduledNotification(
    val notificationId: String,
    val eventId: Long?,
    val eventName: String?,
    val triggerEpochMs: Long
)

enum class FilterType { ALL, BY_LOCATION, BY_TRAINER, BY_TYPE }
