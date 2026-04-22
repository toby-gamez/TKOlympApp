package com.tkolymp.shared.personalevents

import com.tkolymp.shared.storage.OfflineDataStorage
import com.tkolymp.shared.notification.INotificationScheduler
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.datetime.Instant

class PersonalEventService(
    private val offlineDataStorage: OfflineDataStorage,
    private val scheduler: INotificationScheduler
) {
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

    private fun notificationIdFor(eventId: String, minutesBefore: Int) = "personal_${'$'}{eventId}_$minutesBefore"

    suspend fun save(event: PersonalEvent) {
        val list = getAll().toMutableList()
        val existing = list.indexOfFirst { it.id == event.id }
        if (existing >= 0) {
            // cancel previous notifications for that entry
            val prev = list[existing]
            prev.reminderMinutesBefore.forEach { m ->
                try { scheduler.cancelNotification(notificationIdFor(prev.id, m)) } catch (_: Exception) {}
            }
            list[existing] = event
        } else {
            list += event
        }

        try { offlineDataStorage.save(storageKey, json.encodeToString(ListSerializer(PersonalEvent.serializer()), list)) } catch (_: Exception) {}

        // schedule notifications for new event
        event.reminderMinutesBefore.forEach { m ->
            val nid = notificationIdFor(event.id, m)
            try {
                scheduler.scheduleNotificationAt(nid, event.title, event.description, event.startIso, m)
            } catch (_: Exception) {}
        }
    }

    suspend fun delete(id: String) {
        val list = getAll().toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val ev = list.removeAt(idx)
            // cancel reminders
            ev.reminderMinutesBefore.forEach { m -> try { scheduler.cancelNotification(notificationIdFor(ev.id, m)) } catch (_: Exception) {} }
            try { offlineDataStorage.save(storageKey, json.encodeToString(ListSerializer(PersonalEvent.serializer()), list)) } catch (_: Exception) {}
        }
    }
}
