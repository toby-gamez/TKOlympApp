package com.tkolymp.tkolympapp.fakes

import com.tkolymp.shared.auth.IAuthService
import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.event.IEventService
import com.tkolymp.shared.network.IGraphQlClient
import com.tkolymp.shared.notification.INotificationScheduler
import com.tkolymp.shared.systemcalendar.ISystemCalendarService
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class FakeAuthService(private val loginResult: Boolean = true) : IAuthService {
    override suspend fun login(username: String, password: String): Boolean = loginResult
    override suspend fun refreshJwt(): Boolean = false
    override suspend fun hasToken(): Boolean = loginResult
    override suspend fun getToken(): String? = if (loginResult) "fake-token" else null
    override suspend fun initialize() {}
}

class FakeGraphQlClient(private val response: JsonElement = JsonObject(emptyMap())) : IGraphQlClient {
    override suspend fun post(query: String, variables: JsonObject?): JsonElement = response
}

class FakeEventService(
    private val eventById: JsonObject? = null,
    private val throwOnFetch: Boolean = false
) : IEventService {
    override suspend fun fetchEventsGroupedByDay(
        startRangeIso: String, endRangeIso: String, onlyMine: Boolean,
        first: Int, offset: Int, onlyType: String?, cacheNamespace: String?
    ): Map<String, List<EventInstance>> = emptyMap()

    override suspend fun fetchEventById(id: Long, forceRefresh: Boolean): JsonObject? {
        if (throwOnFetch) throw RuntimeException("Network error")
        return eventById
    }
    override suspend fun registerToEventMany(registrations: JsonArray): JsonElement? = null
    override suspend fun setLessonDemand(registrationId: String, trainerId: Int, lessonCount: Int) = false
    override suspend fun deleteEventRegistration(registrationId: String): JsonElement? = null
    override suspend fun setRegistrationNote(registrationId: String, note: String) = false
}

class FakeNotificationScheduler : INotificationScheduler {
    override suspend fun scheduleNotificationAt(notificationId: String, title: String?, text: String?, isoDateTime: String, minutesBefore: Int): Long? = null
    override suspend fun cancelNotification(notificationId: String) {}
    override suspend fun cancelAllNotifications() {}
    override suspend fun requestPermissions(): Boolean = true
}

class FakeSystemCalendarService(private val result: Boolean = false) : ISystemCalendarService {
    override suspend fun addEvent(
        title: String, description: String?, location: String?,
        startMs: Long, endMs: Long, weeklyRepeatCount: Int?
    ): Boolean = result
}

fun minimalEventJson(): JsonObject = buildJsonObject {
    put("id", JsonPrimitive(1L))
    put("name", JsonPrimitive("Test Event"))
    put("type", JsonPrimitive("lesson"))
    put("isRegistrationOpen", JsonPrimitive(true))
    put("isVisible", JsonPrimitive(true))
    put("isPublic", JsonPrimitive(false))
    put("isLocked", JsonPrimitive(false))
    put("enableNotes", JsonPrimitive(false))
    put("eventInstancesList", JsonArray(emptyList()))
    put("eventTrainersList", JsonArray(emptyList()))
    put("eventTargetCohortsList", JsonArray(emptyList()))
    put("eventRegistrationsList", JsonArray(emptyList()))
    put("eventExternalRegistrationsList", JsonArray(emptyList()))
}
