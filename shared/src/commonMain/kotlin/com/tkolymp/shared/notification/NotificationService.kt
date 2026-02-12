package com.tkolymp.shared.notification

import com.tkolymp.shared.event.EventInstance

class NotificationService(
    private val storage: NotificationStorage,
    private val scheduler: INotificationScheduler,
    private val eventService: com.tkolymp.shared.event.IEventService
) {
    suspend fun initializeIfNeeded() {
        val existing = storage.getSettings()
        if (existing == null) {
            val default = NotificationSettings(
                globalEnabled = true,
                rules = listOf()
            )
            storage.saveSettings(default)
        }
    }

    suspend fun getSettings(): NotificationSettings? = storage.getSettings()

    suspend fun updateSettings(s: NotificationSettings) {
        storage.saveSettings(s)
        // caller may want to trigger rescheduleAll()
    }

    suspend fun processEvents(instances: List<EventInstance>) {
        val settings = storage.getSettings() ?: return
        if (!settings.globalEnabled) return

        // Cancel previously scheduled notifications
        val prev = storage.getScheduledNotifications()
        prev.forEach { try { scheduler.cancelNotification(it.notificationId) } catch (_: Throwable) {} }

        val scheduled = mutableListOf<ScheduledNotification>()

        instances.forEach { inst ->
            if (inst.isCancelled) return@forEach
            val since = inst.since ?: return@forEach
            val ev = inst.event ?: return@forEach

            settings.rules.filter { it.enabled }.forEach { rule ->
                // Match using explicit lists if provided, otherwise fall back to old filterType/filterValue
                val matches = when {
                    rule.locations.isNotEmpty() -> {
                        val locName = ev.location?.name ?: ev.locationText ?: ""
                        rule.locations.any { locName.contains(it, ignoreCase = true) }
                    }
                    rule.trainers.isNotEmpty() -> {
                        val trainers = ev.eventTrainersList ?: emptyList()
                        rule.trainers.any { fv -> trainers.any { it.contains(fv, ignoreCase = true) } }
                    }
                    rule.types.isNotEmpty() -> {
                        val evType = ev.type ?: ""
                        rule.types.any { it.equals(evType, ignoreCase = true) }
                    }
                    else -> when (rule.filterType) {
                        FilterType.ALL -> true
                        FilterType.BY_LOCATION -> {
                            val locName = ev.location?.name ?: ev.locationText ?: ""
                            rule.filterValue?.let { fv -> locName.contains(fv, ignoreCase = true) } ?: false
                        }
                        FilterType.BY_TRAINER -> {
                            val trainers = ev.eventTrainersList ?: emptyList()
                            rule.filterValue?.let { fv -> trainers.any { it.contains(fv, ignoreCase = true) } } ?: false
                        }
                        FilterType.BY_TYPE -> {
                            rule.filterValue?.let { fv -> (ev.type ?: "").equals(fv, ignoreCase = true) } ?: false
                        }
                    }
                }

                if (!matches) return@forEach

                rule.timesBeforeMinutes.forEach { minutesBefore ->
                    val nid = "evt_${ev.id}_inst_${inst.id}_$minutesBefore"
                    val trigger = try { scheduler.scheduleNotificationAt(nid, ev.name, "Událost začíná za $minutesBefore minut", since, minutesBefore) } catch (_: Throwable) { null }
                    if (trigger != null) {
                        scheduled += ScheduledNotification(nid, ev.id, ev.name, trigger)
                    }
                }
            }
        }

        storage.saveScheduledNotifications(scheduled)
    }
}
