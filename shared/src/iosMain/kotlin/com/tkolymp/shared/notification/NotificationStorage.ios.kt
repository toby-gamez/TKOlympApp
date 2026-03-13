package com.tkolymp.shared.notification

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
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

    actual suspend fun saveSettings(settings: NotificationSettings) {
        val obj = buildJsonObject {
            put("globalEnabled", JsonPrimitive(settings.globalEnabled))
            put("rules", buildJsonArray {
                settings.rules.forEach { r ->
                    add(buildJsonObject {
                        put("id", JsonPrimitive(r.id))
                        put("name", JsonPrimitive(r.name))
                        put("enabled", JsonPrimitive(r.enabled))
                        put("filterType", JsonPrimitive(r.filterType.name))
                        put("locations", JsonArray(r.locations.map { JsonPrimitive(it) }))
                        put("trainers", JsonArray(r.trainers.map { JsonPrimitive(it) }))
                        put("types", JsonArray(r.types.map { JsonPrimitive(it) }))
                        put("timesBeforeMinutes", JsonArray(r.timesBeforeMinutes.map { JsonPrimitive(it) }))
                    })
                }
            })
        }
        keychainSave("notification_settings", obj.toString())
    }

    actual suspend fun getSettings(): NotificationSettings? {
        val s = keychainGet("notification_settings") ?: return null
        return try {
            val jo = json.parseToJsonElement(s).jsonObject
            val global = jo["globalEnabled"]?.jsonPrimitive?.booleanOrNull ?: true
            val rulesArr = jo["rules"]?.jsonArray ?: JsonArray(emptyList())
            val rules = rulesArr.mapNotNull { rEl ->
                val rj = rEl.jsonObject
                val id = rj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val name = rj["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val enabled = rj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true
                val filterTypeName = rj["filterType"]?.jsonPrimitive?.contentOrNull ?: "BY_LOCATION"
                val filterType = try { FilterType.valueOf(filterTypeName) } catch (e: CancellationException) { throw e } catch (_: Exception) { FilterType.BY_LOCATION }
                val locations = rj["locations"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                val trainers = rj["trainers"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                val types = rj["types"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                val times = rj["timesBeforeMinutes"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull } ?: listOf(60, 5)
                NotificationRule(
                    id = id, name = name, enabled = enabled, filterType = filterType,
                    locations = locations, trainers = trainers, types = types, timesBeforeMinutes = times
                )
            }
            NotificationSettings(globalEnabled = global, rules = rules)
        } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    }

    actual suspend fun saveScheduledNotifications(list: List<ScheduledNotification>) {
        val arr = buildJsonArray {
            list.forEach { sn ->
                add(buildJsonObject {
                    put("notificationId", JsonPrimitive(sn.notificationId))
                    put("eventId", sn.eventId?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("eventName", sn.eventName?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("triggerEpochMs", JsonPrimitive(sn.triggerEpochMs))
                })
            }
        }
        keychainSave("scheduled_notifications", arr.toString())
    }

    actual suspend fun getScheduledNotifications(): List<ScheduledNotification> {
        val s = keychainGet("scheduled_notifications") ?: return emptyList()
        return try {
            json.parseToJsonElement(s).jsonArray.mapNotNull { jo ->
                try {
                    val o = jo.jsonObject
                    val nid = o["notificationId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val eventId = o["eventId"]?.let { if (it is JsonNull) null else it.jsonPrimitive.content.toLongOrNull() }
                    val eventName = o["eventName"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
                    val trigger = o["triggerEpochMs"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                    ScheduledNotification(notificationId = nid, eventId = eventId, eventName = eventName, triggerEpochMs = trigger)
                } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }

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
