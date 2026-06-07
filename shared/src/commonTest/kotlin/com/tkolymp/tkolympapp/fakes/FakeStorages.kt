package com.tkolymp.tkolympapp.fakes

import com.tkolymp.shared.notification.BirthdayNotificationSettings
import com.tkolymp.shared.notification.EventReminder
import com.tkolymp.shared.notification.INotificationStorage
import com.tkolymp.shared.notification.NotificationRule
import com.tkolymp.shared.notification.NotificationSettings
import com.tkolymp.shared.notification.ReceivedMessage
import com.tkolymp.shared.notification.ScheduledNotification
import com.tkolymp.shared.storage.ICalendarPreferenceStorage
import com.tkolymp.shared.storage.ITokenStorage
import com.tkolymp.shared.storage.IUserStorage
import com.tkolymp.shared.storage.OfflineDataStorage

class FakeTokenStorage(private var token: String? = null) : ITokenStorage {
    override suspend fun saveToken(token: String) { this.token = token }
    override suspend fun getToken(): String? = token
    override suspend fun clear() { token = null }
}

class FakeUserStorage : IUserStorage {
    private var personId: String? = null
    private var cstsId: String? = null
    private var coupleIds: List<String> = emptyList()
    private var currentUserJson: String? = null
    private var personDetailsJson: String? = null

    override suspend fun savePersonId(personId: String) { this.personId = personId }
    override suspend fun getPersonId(): String? = personId
    override suspend fun saveCstsId(cstsId: String) { this.cstsId = cstsId }
    override suspend fun getCstsId(): String? = cstsId
    override suspend fun saveCoupleIds(coupleIds: List<String>) { this.coupleIds = coupleIds }
    override suspend fun getCoupleIds(): List<String> = coupleIds
    override suspend fun saveCurrentUserJson(json: String) { this.currentUserJson = json }
    override suspend fun getCurrentUserJson(): String? = currentUserJson
    override suspend fun savePersonDetailsJson(json: String) { this.personDetailsJson = json }
    override suspend fun getPersonDetailsJson(): String? = personDetailsJson
    override suspend fun clear() {
        personId = null; cstsId = null; coupleIds = emptyList()
        currentUserJson = null; personDetailsJson = null
    }
}

class FakeCalendarPreferenceStorage : ICalendarPreferenceStorage {
    private val eventIds = mutableSetOf<Long>()
    override suspend fun getPreferTimeline(): Boolean = false
    override suspend fun setPreferTimeline(value: Boolean) {}
    override suspend fun getThemeMode(): String = "system"
    override suspend fun setThemeMode(value: String) {}
    override suspend fun isEventInCalendar(eventId: Long): Boolean = eventIds.contains(eventId)
    override suspend fun setEventInCalendar(eventId: Long) { eventIds.add(eventId) }
}

class FakeNotificationStorage : INotificationStorage {
    private var settings: NotificationSettings? = null
    private var reminders: List<EventReminder> = emptyList()
    private var received: List<ReceivedMessage> = emptyList()
    private var scheduled: List<ScheduledNotification> = emptyList()

    override suspend fun saveSettings(settings: NotificationSettings) { this.settings = settings }
    override suspend fun getSettings(): NotificationSettings? = settings
    override suspend fun saveScheduledNotifications(list: List<ScheduledNotification>) { scheduled = list }
    override suspend fun getScheduledNotifications(): List<ScheduledNotification> = scheduled
    override suspend fun saveReceivedNotifications(list: List<ReceivedMessage>) { received = list }
    override suspend fun getReceivedNotifications(): List<ReceivedMessage> = received
    override suspend fun saveEventReminders(list: List<EventReminder>) { reminders = list }
    override suspend fun getEventReminders(): List<EventReminder> = reminders
    override suspend fun saveBirthdaySettings(settings: BirthdayNotificationSettings) {}
    override suspend fun getBirthdaySettings(): BirthdayNotificationSettings? = null
    override suspend fun saveScheduledBirthdayNotificationIds(ids: List<String>) {}
    override suspend fun getScheduledBirthdayNotificationIds(): List<String> = emptyList()
}

class FakeOfflineDataStorage : OfflineDataStorage {
    private val store = mutableMapOf<String, String>()
    override suspend fun save(key: String, json: String) { store[key] = json }
    override suspend fun load(key: String): String? = store[key]
    override suspend fun deleteByPrefix(prefix: String) { store.keys.removeAll { it.startsWith(prefix) } }
    override suspend fun allKeys(): Set<String> = store.keys.toSet()
}
