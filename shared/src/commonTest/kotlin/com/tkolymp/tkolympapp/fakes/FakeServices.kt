package com.tkolymp.tkolympapp.fakes

import com.tkolymp.shared.announcements.Announcement
import com.tkolymp.shared.announcements.IAnnouncementService
import com.tkolymp.shared.auth.IAuthService
import com.tkolymp.shared.competitions.Competition
import com.tkolymp.shared.competitions.ICompetitionService
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
    private val throwOnFetch: Boolean = false,
    private val groupedByDay: Map<String, List<EventInstance>> = emptyMap(),
    private val throwOnGroupedFetch: Boolean = false
) : IEventService {
    override suspend fun fetchEventsGroupedByDay(
        startRangeIso: String, endRangeIso: String, onlyMine: Boolean,
        first: Int, offset: Int, onlyType: String?, cacheNamespace: String?
    ): Map<String, List<EventInstance>> {
        if (throwOnGroupedFetch) throw RuntimeException("Network error")
        return groupedByDay
    }

    override suspend fun fetchEventById(id: Long, forceRefresh: Boolean): JsonObject? {
        if (throwOnFetch) throw RuntimeException("Network error")
        return eventById
    }
    override suspend fun registerToEventMany(registrations: JsonArray): JsonElement? = null
    override suspend fun setLessonDemand(registrationId: String, trainerId: Int, lessonCount: Int) = false
    override suspend fun deleteEventRegistration(registrationId: String): JsonElement? = null
    override suspend fun setRegistrationNote(registrationId: String, note: String) = false
}

class FakeAnnouncementService(
    private val announcements: List<Announcement> = emptyList(),
    private val throwOnFetch: Boolean = false
) : IAnnouncementService {
    override suspend fun getAnnouncements(sticky: Boolean): List<Announcement> {
        if (throwOnFetch) throw RuntimeException("Network error")
        return announcements
    }
    override suspend fun getAnnouncementById(id: Long, forceRefresh: Boolean): Announcement? = null
}

class FakeCompetitionService(
    private val upcoming: List<Competition> = emptyList(),
    private val past: List<Competition> = emptyList()
) : ICompetitionService {
    override suspend fun getUpcomingCompetitions(pSince: String?, pUntil: String?, first: Int): List<Competition> = upcoming
    override suspend fun getPastCompetitions(pSince: String?, pUntil: String?, first: Int): List<Competition> = past
    override suspend fun getNearestUpcoming(): Competition? = upcoming.firstOrNull()
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
