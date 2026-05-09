package com.tkolymp.shared.notification

import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.language.AppStrings
import kotlinx.coroutines.CancellationException
import com.tkolymp.shared.models.UserRole
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.todayIn

class NotificationService(
    private val storage: NotificationStorage,
    private val scheduler: INotificationScheduler,
    private val eventService: com.tkolymp.shared.event.IEventService
) {
    suspend fun initializeIfNeeded(userRole: UserRole? = null) {
        val existing = storage.getSettings()
        if (existing == null) {
            // If caller didn't provide a role, try to read persisted onboarding role.
            val roleToCheck = userRole ?: try {
                com.tkolymp.shared.ServiceLocator.onboardingStorage.getUserRole()
            } catch (_: Exception) { null }

            // If role is unknown, skip creating defaults now — wait until onboarding completes.
            if (roleToCheck == null) return

            // Do not create default notification rule for parents.
            if (roleToCheck == UserRole.PARENT) {
                val default = NotificationSettings(
                    globalEnabled = false,
                    rules = listOf()
                )
                storage.saveSettings(default)
                return
            }

            val defaultRule = NotificationRule(
                id = "default-rule",
                name = AppStrings.current.notifications.defaultRuleName,
                enabled = true,
                filterType = FilterType.ALL,
                timesBeforeMinutes = listOf(15)
            )
            val default = NotificationSettings(
                globalEnabled = true,
                rules = listOf(defaultRule)
            )
            storage.saveSettings(default)
        }
    }

    suspend fun getSettings(): NotificationSettings? = storage.getSettings()

    suspend fun updateSettings(s: NotificationSettings) {
        storage.saveSettings(s)
        // caller may want to trigger rescheduleAll()
    }

    suspend fun getBirthdaySettings(): BirthdayNotificationSettings =
        storage.getBirthdaySettings() ?: BirthdayNotificationSettings()

    suspend fun saveBirthdaySettings(settings: BirthdayNotificationSettings) {
        storage.saveBirthdaySettings(settings)
        scheduleBirthdayNotifications(settings = settings)
    }

    /**
     * Schedules (or cancels) birthday alarms based on current settings.
     * Call after saving settings and on app startup.
     * Pass [peopleService] to avoid an extra network call if people are already loaded.
     */
    suspend fun scheduleBirthdayNotifications(
        settings: BirthdayNotificationSettings? = null,
        peopleService: com.tkolymp.shared.people.PeopleService? = null
    ) {
        val resolvedSettings = settings ?: getBirthdaySettings()

        // Cancel all existing birthday alarms first
        val prevIds = storage.getScheduledBirthdayNotificationIds()
        prevIds.forEach { try { scheduler.cancelNotification(it) } catch (_: Exception) {} }

        if (!resolvedSettings.enabled) {
            storage.saveScheduledBirthdayNotificationIds(emptyList())
            return
        }

        val svc = peopleService ?: com.tkolymp.shared.ServiceLocator.peopleService
        val allPeople = try { svc.fetchPeople() } catch (_: Exception) { return }

        // Resolve which person IDs to notify
        val targetIds: Set<String> = if (resolvedSettings.notifyAll) {
            allPeople.mapNotNull { it.id }.toSet()
        } else {
            val fromCohorts = allPeople.filter { person ->
                person.cohortMembershipsList.any { m ->
                    m.cohort?.id != null && resolvedSettings.selectedCohortIds.contains(m.cohort.id)
                }
            }.mapNotNull { it.id }.toSet()
            resolvedSettings.selectedPersonIds.toSet() + fromCohorts
        }

        val tz = TimeZone.currentSystemDefault()
        val today = kotlin.time.Clock.System.todayIn(tz)
        val scheduledIds = mutableListOf<String>()

        allPeople.filter { it.id in targetIds }.forEach { person ->
            val birthStr = person.birthDate?.takeIf { it.length >= 10 } ?: return@forEach
            val birthDate = try { LocalDate.parse(birthStr.take(10)) } catch (_: Exception) { return@forEach }

            val thisYear = today.year
            val period = DatePeriod(days = resolvedSettings.daysBefore)
            val thisYearBirthday = LocalDate(thisYear, birthDate.month, birthDate.dayOfMonth)
            val thisYearTrigger = thisYearBirthday.minus(period)
            val triggerDate = if (thisYearTrigger >= today) thisYearTrigger
            else LocalDate(thisYear + 1, birthDate.month, birthDate.dayOfMonth).minus(period)

            val triggerInstant = triggerDate.atTime(resolvedSettings.notificationHour, 0).toInstant(tz)
            val nid = "bday_${person.id}"
            val name = listOfNotNull(person.firstName, person.lastName).joinToString(" ").ifBlank { person.id }
            try {
                scheduler.scheduleNotificationAt(
                    notificationId = nid,
                    title = AppStrings.current.notifications.birthdayNotificationsTitle,
                    text = name,
                    isoDateTime = triggerInstant.toString(),
                    minutesBefore = 0
                )
                scheduledIds += nid
            } catch (_: Exception) {}
        }

        storage.saveScheduledBirthdayNotificationIds(scheduledIds)
    }

    suspend fun processEvents(instances: List<EventInstance>) {
        val settings = storage.getSettings() ?: return
        if (!settings.globalEnabled) return

        // Cancel previously scheduled notifications
        val prev = storage.getScheduledNotifications()
        prev.forEach { try { scheduler.cancelNotification(it.notificationId) } catch (e: CancellationException) { throw e } catch (_: Exception) {} }

        val scheduled = mutableListOf<ScheduledNotification>()

        instances.forEach { inst ->
            if (inst.isCancelled) return@forEach
            val since = inst.since ?: return@forEach
            val ev = inst.event ?: return@forEach

            settings.rules.filter { it.enabled }.forEach { rule ->
                // Match using explicit lists if provided.
                // If no explicit values were selected for a BY_* filter, treat that as "match all" for that dimension.
                val matches = when {
                    rule.locations.isNotEmpty() -> {
                        val locName = ev.location?.name ?: ev.locationText ?: ""
                        rule.locations.any { locName.contains(it, ignoreCase = true) }
                    }
                    rule.trainers.isNotEmpty() -> {
                        val trainers = ev.eventTrainersList
                        rule.trainers.any { fv -> trainers.any { it.contains(fv, ignoreCase = true) } }
                    }
                    rule.types.isNotEmpty() -> {
                        val evType = ev.type ?: ""
                        rule.types.any { it.equals(evType, ignoreCase = true) }
                    }
                    else -> when (rule.filterType) {
                        FilterType.ALL -> true
                        // If no explicit values were selected for BY_* filters, treat as match-all
                        FilterType.BY_LOCATION -> true
                        FilterType.BY_TRAINER -> true
                        FilterType.BY_TYPE -> true
                    }
                }

                if (!matches) return@forEach

                rule.timesBeforeMinutes.forEach { minutesBefore ->
                    val nid = "evt_${ev.id}_inst_${inst.id}_$minutesBefore"

                    val titleToShow: String? = ev.name?.takeIf { it.isNotBlank() } ?: run {
                        val evType = ev.type ?: ""
                        if (evType.equals("LESSON", ignoreCase = true)) {
                            val trainers = ev.eventTrainersList
                            if (trainers.isNotEmpty()) trainers.joinToString(", ") else AppStrings.current.events.eventTypeLesson.replaceFirstChar { it.titlecase() }
                        } else {
                            // fallback to event type or generic label
                            evType.ifEmpty { AppStrings.current.events.event }
                        }
                    }

                    val trigger = try { scheduler.scheduleNotificationAt(nid, titleToShow, AppStrings.current.notifications.notificationEventStartsIn.replace("{0}", minutesBefore.toString()), since, minutesBefore) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                    if (trigger != null) {
                        scheduled += ScheduledNotification(nid, ev.id, titleToShow, trigger)
                    }
                }
            }
        }

        storage.saveScheduledNotifications(scheduled)
    }

    suspend fun getReminders(): List<EventReminder> = storage.getEventReminders()

    suspend fun getReminderForEvent(eventId: Long): EventReminder? =
        storage.getEventReminders().find { it.eventId == eventId }

    suspend fun addOrUpdateReminder(reminder: EventReminder): EventReminder {
        val existing = storage.getEventReminders().find { it.eventId == reminder.eventId }
        if (existing?.scheduledNotificationId != null) {
            try { scheduler.cancelNotification(existing.scheduledNotificationId) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
        val nid = "reminder_evt_${reminder.eventId}"
        val title = reminder.eventName.ifBlank { AppStrings.current.events.event }
        val body = AppStrings.current.notifications.notificationEventStartsIn.replace("{0}", reminder.minutesBefore.toString())
        val trigger = try {
            scheduler.scheduleNotificationAt(nid, title, body, reminder.eventStartIso, reminder.minutesBefore)
        } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        val saved = reminder.copy(id = nid, scheduledNotificationId = if (trigger != null) nid else null)
        val others = storage.getEventReminders().filter { it.eventId != reminder.eventId }
        storage.saveEventReminders(others + saved)
        return saved
    }

    suspend fun deleteReminder(reminderId: String) {
        val all = storage.getEventReminders()
        val toDelete = all.find { it.id == reminderId }
        toDelete?.scheduledNotificationId?.let {
            try { scheduler.cancelNotification(it) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
        storage.saveEventReminders(all.filter { it.id != reminderId })
    }
}
