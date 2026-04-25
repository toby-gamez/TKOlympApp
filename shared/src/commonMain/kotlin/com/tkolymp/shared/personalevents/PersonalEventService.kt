package com.tkolymp.shared.personalevents

import com.tkolymp.shared.notification.FilterType
import com.tkolymp.shared.notification.INotificationScheduler
import com.tkolymp.shared.notification.NotificationStorage
import com.tkolymp.shared.storage.OfflineDataStorage
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Instant

class PersonalEventService(
    private val offlineDataStorage: OfflineDataStorage,
    private val scheduler: INotificationScheduler,
    private val notificationStorage: NotificationStorage? = null
) {
    private val mutex = kotlinx.coroutines.sync.Mutex()
    private val storageKey = "personal_events_v1"
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun getAll(): List<PersonalEvent> {
        val raw = try { offlineDataStorage.load(storageKey) } catch (_: Exception) { null }
        if (raw.isNullOrBlank()) return emptyList()
        return try { json.decodeFromString(ListSerializer(PersonalEvent.serializer()), raw) } catch (_: Exception) { emptyList() }
    }

    suspend fun getInRange(startIso: String, endIso: String): List<PersonalEvent> {
        val start = try { Instant.parse(startIso) } catch (_: Exception) { return emptyList() }
        val end = try { Instant.parse(endIso) } catch (_: Exception) { return emptyList() }

        return getAll().filter { ev ->
            try {
                val evStart = Instant.parse(ev.startIso)
                evStart >= start && evStart < end
            } catch (_: Exception) { false }
        }
    }

    private fun notificationIdFor(eventId: String, minutesBefore: Int) = "personal_${eventId}_$minutesBefore"
    private fun ruleNotificationIdFor(ruleId: String, eventId: String, minutesBefore: Int) = "personal_rule_${ruleId}_${eventId}_$minutesBefore"

    private suspend fun cancelRuleNotifications(eventId: String) {
        val settings = try { notificationStorage?.getSettings() } catch (_: Exception) { null } ?: return
        settings.rules.forEach { rule ->
            rule.timesBeforeMinutes.forEach { m ->
                try { scheduler.cancelNotification(ruleNotificationIdFor(rule.id, eventId, m)) } catch (_: Exception) {}
            }
        }
    }

    private suspend fun scheduleRuleNotifications(event: PersonalEvent) {
        val settings = try { notificationStorage?.getSettings() } catch (_: Exception) { null } ?: return
        if (!settings.globalEnabled) return
        settings.rules.filter { it.enabled }.forEach { rule ->
            val matches = when (rule.filterType) {
                FilterType.ALL -> true
                FilterType.BY_TYPE -> rule.types.contains("PERSONAL_TRAINING")
                else -> false
            }
            if (!matches) return@forEach
            rule.timesBeforeMinutes.forEach { m ->
                val nid = ruleNotificationIdFor(rule.id, event.id, m)
                try { scheduler.scheduleNotificationAt(nid, event.title, event.description, event.startIso, m) } catch (_: Exception) {}
            }
        }
    }

    suspend fun rescheduleAllPersonalEvents() {
        val settings = try { notificationStorage?.getSettings() } catch (_: Exception) { null } ?: return
        val relevantRules = settings.rules.filter { it.enabled && (it.filterType == FilterType.ALL || (it.filterType == FilterType.BY_TYPE && it.types.contains("PERSONAL_TRAINING"))) }
        if (relevantRules.isEmpty() && !settings.globalEnabled) return
        val events = try { getAll() } catch (_: Exception) { return }
        events.forEach { event ->
            cancelRuleNotifications(event.id)
            if (settings.globalEnabled) scheduleRuleNotifications(event)
        }
    }

    suspend fun save(event: PersonalEvent) {
        mutex.withLock {
            val list = getAll().toMutableList()
        val existing = list.indexOfFirst { it.id == event.id }
        if (existing >= 0) {
            val prev = list[existing]
            prev.reminderMinutesBefore.forEach { m ->
                try { scheduler.cancelNotification(notificationIdFor(prev.id, m)) } catch (_: Exception) {}
            }
            cancelRuleNotifications(prev.id)
            list[existing] = event
        } else {
            list += event
        }

        try { offlineDataStorage.save(storageKey, json.encodeToString(ListSerializer(PersonalEvent.serializer()), list)) } catch (_: Exception) {}

        event.reminderMinutesBefore.forEach { m ->
            val nid = notificationIdFor(event.id, m)
            try { scheduler.scheduleNotificationAt(nid, event.title, event.description, event.startIso, m) } catch (_: Exception) {}
        }

        scheduleRuleNotifications(event)
        }
    }

    suspend fun delete(id: String) {
        mutex.withLock {
            val list = getAll().toMutableList()
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) {
                val ev = list.removeAt(idx)
                ev.reminderMinutesBefore.forEach { m -> try { scheduler.cancelNotification(notificationIdFor(ev.id, m)) } catch (_: Exception) {} }
                cancelRuleNotifications(ev.id)
                try { offlineDataStorage.save(storageKey, json.encodeToString(ListSerializer(PersonalEvent.serializer()), list)) } catch (_: Exception) {}
            }
        }
    }
}
