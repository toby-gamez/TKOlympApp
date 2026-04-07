package com.tkolymp.shared.notification

import android.content.Context
import kotlinx.coroutines.CancellationException
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

actual class NotificationStorage actual constructor(platformContext: Any) {
    private val context = platformContext as Context
    private val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
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
        val s = obj.toString()
        prefs.edit().putString("notification_settings", s).apply()
    }

    actual suspend fun getSettings(): NotificationSettings? {
        val s = prefs.getString("notification_settings", null) ?: return null
        return try {
            val el = json.parseToJsonElement(s)
            val jo = el.jsonObject
            val global = jo["globalEnabled"]?.jsonPrimitive?.booleanOrNull ?: true
            val rulesArr = jo["rules"]?.jsonArray ?: JsonArray(emptyList())
            val rules = rulesArr.mapNotNull { it.jsonObject.let { rj ->
                val id = rj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val name = rj["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val enabled = rj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true
                val filterTypeName = rj["filterType"]?.jsonPrimitive?.contentOrNull ?: "BY_LOCATION"
                val filterType = try { FilterType.valueOf(filterTypeName) } catch (e: CancellationException) { throw e } catch (_: Exception) { FilterType.BY_LOCATION }
                val locations = rj["locations"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                val trainers = rj["trainers"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                val types = rj["types"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                val times = rj["timesBeforeMinutes"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull } ?: listOf(60, 5)
                NotificationRule(id = id, name = name, enabled = enabled, filterType = filterType, locations = locations, trainers = trainers, types = types, timesBeforeMinutes = times)
            } }
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
        val s = arr.toString()
        prefs.edit().putString("scheduled_notifications", s).apply()
    }

    actual suspend fun getScheduledNotifications(): List<ScheduledNotification> {
        val s = prefs.getString("scheduled_notifications", null) ?: return emptyList()
        return try {
            val el = json.parseToJsonElement(s)
            val arr = el.jsonArray
            arr.mapNotNull { jo ->
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

    actual suspend fun saveReceivedNotifications(list: List<ReceivedMessage>) {
        val arr = buildJsonArray {
            list.forEach { rm ->
                add(buildJsonObject {
                    put("id", JsonPrimitive(rm.id))
                    put("title", rm.title?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("body", rm.body?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("sender", rm.sender?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("topic", rm.topic?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("epochMs", JsonPrimitive(rm.epochMs))
                })
            }
        }
        val s = arr.toString()
        prefs.edit().putString("received_notifications", s).apply()
    }

    actual suspend fun getReceivedNotifications(): List<ReceivedMessage> {
        val s = prefs.getString("received_notifications", null) ?: return emptyList()
        return try {
            val el = json.parseToJsonElement(s)
            val arr = el.jsonArray
            arr.mapNotNull { jo ->
                try {
                    val o = jo.jsonObject
                    val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val title = o["title"]?.jsonPrimitive?.contentOrNull
                    val body = o["body"]?.jsonPrimitive?.contentOrNull
                    val sender = o["sender"]?.jsonPrimitive?.contentOrNull
                        val topic = o["topic"]?.jsonPrimitive?.contentOrNull
                    val epoch = o["epochMs"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                        ReceivedMessage(id = id, title = title, body = body, sender = sender, topic = topic, epochMs = epoch)
                } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }

    actual suspend fun saveEventReminders(list: List<EventReminder>) {
        val arr = buildJsonArray {
            list.forEach { r ->
                add(buildJsonObject {
                    put("id", JsonPrimitive(r.id))
                    put("eventId", JsonPrimitive(r.eventId))
                    put("eventName", JsonPrimitive(r.eventName))
                    put("eventStartIso", JsonPrimitive(r.eventStartIso))
                    put("minutesBefore", JsonPrimitive(r.minutesBefore))
                    put("scheduledNotificationId", r.scheduledNotificationId?.let { JsonPrimitive(it) } ?: JsonNull)
                })
            }
        }
        prefs.edit().putString("event_reminders", arr.toString()).apply()
    }

    actual suspend fun getEventReminders(): List<EventReminder> {
        val s = prefs.getString("event_reminders", null) ?: return emptyList()
        return try {
            json.parseToJsonElement(s).jsonArray.mapNotNull { jo ->
                try {
                    val o = jo.jsonObject
                    val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val eventId = o["eventId"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                    val eventName = o["eventName"]?.jsonPrimitive?.contentOrNull ?: ""
                    val startIso = o["eventStartIso"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val minutes = o["minutesBefore"]?.jsonPrimitive?.intOrNull ?: 30
                    val notifId = o["scheduledNotificationId"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
                    EventReminder(id = id, eventId = eventId, eventName = eventName, eventStartIso = startIso, minutesBefore = minutes, scheduledNotificationId = notifId)
                } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }
}
