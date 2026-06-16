package com.tkolymp.shared.notification

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

@OptIn(ExperimentalForeignApi::class)
actual class NotificationStorage actual constructor(platformContext: Any) {

    private val service = "com.tkolymp.tkolympapp"
    private val json = Json { ignoreUnknownKeys = true }

    private inline fun <reified T> save(key: String, value: T) {
        keychainSave(key, json.encodeToString(value))
    }

    private inline fun <reified T> load(key: String): T? {
        val s = keychainGet(key) ?: return null
        return try {
            json.decodeFromString<T>(s)
        } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    }

    actual suspend fun saveSettings(settings: NotificationSettings) = save("notification_settings", settings)
    actual suspend fun getSettings(): NotificationSettings? = load("notification_settings")

    actual suspend fun saveScheduledNotifications(list: List<ScheduledNotification>) = save("scheduled_notifications", list)
    actual suspend fun getScheduledNotifications(): List<ScheduledNotification> = load("scheduled_notifications") ?: emptyList()

    actual suspend fun saveReceivedNotifications(list: List<ReceivedMessage>) = save("received_notifications", list)
    actual suspend fun getReceivedNotifications(): List<ReceivedMessage> = load("received_notifications") ?: emptyList()

    actual suspend fun saveEventReminders(list: List<EventReminder>) = save("event_reminders", list)
    actual suspend fun getEventReminders(): List<EventReminder> = load("event_reminders") ?: emptyList()

    actual suspend fun saveBirthdaySettings(settings: BirthdayNotificationSettings) = save("birthday_notification_settings", settings)
    actual suspend fun getBirthdaySettings(): BirthdayNotificationSettings? = load("birthday_notification_settings")

    actual suspend fun saveScheduledBirthdayNotificationIds(ids: List<String>) = save("scheduled_birthday_ids", ids)
    actual suspend fun getScheduledBirthdayNotificationIds(): List<String> = load("scheduled_birthday_ids") ?: emptyList()

    private fun keychainSave(account: String, value: String) {
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        keychainDelete(account)
        val query = NSMutableDictionary().apply {
            setObject(kSecClassGenericPassword, kSecClass)
            setObject(service, kSecAttrService)
            setObject(account, kSecAttrAccount)
            setObject(data, kSecValueData)
        }
        SecItemAdd(query as CFDictionaryRef, null)
    }

    private fun keychainGet(account: String): String? = memScoped {
        val query = NSMutableDictionary().apply {
            setObject(kSecClassGenericPassword, kSecClass)
            setObject(service, kSecAttrService)
            setObject(account, kSecAttrAccount)
            setObject(kCFBooleanTrue, kSecReturnData)
            setObject(kSecMatchLimitOne, kSecMatchLimit)
        }
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)
        if (status != 0) return@memScoped null
        val data = result.value as? NSData ?: return@memScoped null
        NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
    }

    private fun keychainDelete(account: String) {
        val query = NSMutableDictionary().apply {
            setObject(kSecClassGenericPassword, kSecClass)
            setObject(service, kSecAttrService)
            setObject(account, kSecAttrAccount)
        }
        SecItemDelete(query as CFDictionaryRef)
    }
}
