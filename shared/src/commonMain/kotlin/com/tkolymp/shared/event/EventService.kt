package com.tkolymp.shared.event

import com.tkolymp.shared.Logger
import kotlinx.coroutines.CancellationException
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.cache.CacheService
import kotlin.time.Duration.Companion.minutes
import com.tkolymp.shared.network.IGraphQlClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

interface IEventService {
    /**
     * Fetch event instances between ISO datetimes `startRange` and `endRange`.
     * `onlyMine` will be sent as a variable to the server so the backend can filter.
     * Returns a map keyed by date (yyyy-MM-dd) with a list of instances for that day.
     */
    suspend fun fetchEventsGroupedByDay(
        startRangeIso: String,
        endRangeIso: String,
        onlyMine: Boolean = false,
        first: Int = 200,
        offset: Int = 0,
        onlyType: String? = null,
        cacheNamespace: String? = null
    ): Map<String, List<EventInstance>>

    /**
     * Fetch full event object (raw JsonObject) by id using the EventFull fragment.
     * Returns the `event` JsonObject from the GraphQL response or null on error.
     */
    suspend fun fetchEventById(id: BigInt, forceRefresh: Boolean = false): JsonObject?

    // Register (isRegistered=true) or cancel (isRegistered=false) for a single event instance.
    // lessonTrainerIds and lessonCounts must be the same length when provided.
    // Throws on GraphQL error; returns null on network error.
    suspend fun setEventInstanceRegistration(
        instanceId: Long,
        personId: Long?,
        coupleId: Long?,
        isRegistered: Boolean,
        note: String? = null,
        lessonTrainerIds: List<Long>? = null,
        lessonCounts: List<Int>? = null
    ): JsonObject?

    // Set lesson demand for a single registration/trainer pair (instanceRegistrationId + instanceTrainerId).
    suspend fun setLessonDemand(registrationId: String, trainerId: Long, lessonCount: Int): Boolean
}

// Notes: GraphQL types
// - `id` is BigInt (use `BigInt` alias)
// - `since`, `until`, `updatedAt` are Datetime (use `DateTime` alias)
typealias BigInt = Long
typealias DateTime = String

@Serializable
data class EventInstance(
    val id: BigInt,
    val isCancelled: Boolean,
    val since: DateTime? = null,
    val until: DateTime? = null,
    val updatedAt: DateTime? = null,
    val event: Event? = null
)

@Serializable
data class Cohort(val id: BigInt? = null, val name: String? = null, val colorRgb: String? = null)

@Serializable
data class TargetCohort(val cohortId: BigInt? = null, val cohort: Cohort? = null)

@Serializable
data class Person(val id: BigInt? = null, val name: String? = null, val firstName: String? = null, val lastName: String? = null)

@Serializable
data class SimpleName(val firstName: String? = null, val lastName: String? = null)

@Serializable
data class Couple(val id: BigInt? = null, val man: SimpleName? = null, val woman: SimpleName? = null)

@Serializable
data class Registration(val id: BigInt? = null, val person: Person? = null, val couple: Couple? = null)

@Serializable
data class Location(val id: BigInt? = null, val name: String? = null)

@Serializable
data class Event(
    val id: BigInt? = null,
    val name: String? = null,
    val description: String? = null,
    val type: String? = null,
    val locationText: String? = null,
    val isRegistrationOpen: Boolean = false,
    val isVisible: Boolean = false,
    val isPublic: Boolean = false,
    val eventTrainersList: List<String> = emptyList(),
    val eventTargetCohortsList: List<TargetCohort> = emptyList(),
    val eventRegistrationsList: List<Registration> = emptyList(),
    val location: Location? = null
)

class EventService(
    private val client: IGraphQlClient = ServiceLocator.graphQlClient,
    private val cache: CacheService = ServiceLocator.cacheService
) : IEventService {
    

    private val eventByIdQuery = """
            query EventDetail(${'$'}id: BigInt!) {
                eventInstance(id: ${'$'}id) {
                    id
                    type
                    name
                    summary
                    description
                    since
                    until
                    isCancelled
                    capacity
                    remainingPersonSpots
                    isLocked
                    isVisible
                    isPublic
                    enableNotes
                    locationText
                    location {
                        id
                        name
                        __typename
                    }
                    eventTrainersList: eventInstanceTrainersByInstanceIdList {
                        id
                        personId
                        lessonsOffered
                        lessonsRemaining
                        person {
                            id
                            name
                            __typename
                        }
                        __typename
                    }
                    eventInstancesList: childEventInstancesList(orderBy: SINCE_ASC) {
                        id
                        since
                        until
                        isCancelled
                        __typename
                    }
                    eventTargetCohortsList: targetCohortsList {
                        cohortId
                        cohort {
                            id
                            name
                            colorRgb
                            __typename
                        }
                        __typename
                    }
                    eventRegistrationsList: eventInstanceRegistrationsByInstanceIdList(
                        condition: { registrationStatus: ACTIVE }
                    ) {
                        id
                        note
                        personId
                        person {
                            id
                            name
                            firstName
                            lastName
                            __typename
                        }
                        coupleId
                        couple {
                            id
                            man {
                                id
                                name
                                firstName
                                lastName
                                __typename
                            }
                            woman {
                                id
                                name
                                firstName
                                lastName
                                __typename
                            }
                            __typename
                        }
                        eventLessonDemandsByRegistrationIdList {
                            id
                            lessonCount
                            trainerId
                            __typename
                        }
                        createdAt
                        __typename
                    }
                    eventExternalRegistrationsList: eventExternalRegistrationsByInstanceIdList {
                        id
                        note
                        email
                        firstName
                        lastName
                        __typename
                    }
                    __typename
                }
            }
    """.trimIndent()

        private val query = """
                query MyQuery(
                    $${"startRange"}: Datetime!,
                    $${"endRange"}: Datetime!,
                    $${"first"}: Int,
                    $${"offset"}: Int,
                    $${"onlyType"}: EventType,
                    $${"onlyMine"}: Boolean
                ) {
                    eventInstancesForRangeList(startRange: $${"startRange"}, endRange: $${"endRange"}, first: $${"first"}, offset: $${"offset"}, onlyType: $${"onlyType"}, onlyMine: $${"onlyMine"}) {
                        id
                        isCancelled
                        since
                        until
                        updatedAt
                        name
                        description
                        type
                        locationText
                        isVisible
                        isPublic
                        trainersList { person { name } }
                        targetCohortsList { cohortId cohort { id name colorRgb } }
                        eventInstanceRegistrationsByInstanceIdList {
                            id
                            person { id name firstName lastName }
                            couple { id man { firstName lastName } woman { firstName lastName } }
                        }
                        location { id name }
                    }
                }
        """.trimIndent()

    override suspend fun fetchEventsGroupedByDay(
        startRangeIso: String,
        endRangeIso: String,
        onlyMine: Boolean,
        first: Int,
        offset: Int,
        onlyType: String?,
        cacheNamespace: String?
    ): Map<String, List<EventInstance>> {
        val ns = cacheNamespace ?: "events"
        val cacheKey = "${ns}_${startRangeIso}_${endRangeIso}_${onlyMine}_${first}_${offset}_${onlyType}"
            val cached = try {
                cache.get<Map<String, List<EventInstance>>>(cacheKey)
            } catch (e: CancellationException) { throw e } catch (t: Exception) {
                Logger.d("EventService", "fetchEventsGroupedByDay: cache.get failed for $cacheKey: ${t.message}")
                null
            }
            if (cached != null) {
                Logger.d("EventService", "fetchEventsGroupedByDay: cache HIT for $cacheKey")
                return cached
            } else {
                Logger.d("EventService", "fetchEventsGroupedByDay: cache MISS for $cacheKey")
            }
        val variables = buildJsonObject {
            put("startRange", JsonPrimitive(startRangeIso))
            put("endRange", JsonPrimitive(endRangeIso))
            put("first", JsonPrimitive(first))
            put("offset", JsonPrimitive(offset))
            put("onlyMine", JsonPrimitive(onlyMine))
            if (onlyType != null) put("onlyType", JsonPrimitive(onlyType))
        }

        val resp = try {
            client.post(query, variables)
        } catch (ex: Exception) {
            Logger.d("EventService", "fetchEventsGroupedByDay: network error for $cacheKey: ${ex.message}")
            throw ex
        }

        val instances = mutableListOf<EventInstance>()

        val data = resp.jsonObject["data"]?.jsonObject
        if (data == null) {
            val errors = resp.jsonObject["errors"]
            Logger.d("EventService", "fetchEventsGroupedByDay: GraphQL error for $cacheKey: $errors")
            throw Exception("GraphQL error: $errors")
        }
        val listElem = data.get("eventInstancesForRangeList") ?: return emptyMap()

        if (listElem is JsonNull) return emptyMap()

        val array = when (listElem) {
            is JsonArray -> listElem
            else -> return emptyMap()
        }

        array.forEach { el ->
            if (el !is JsonObject) return@forEach
            val obj = el
            val idPrim = obj["id"]?.jsonPrimitive ?: return@forEach
            val id = idPrim.longOrNull ?: idPrim.contentOrNull?.toLongOrNull() ?: return@forEach
            val isCancelled = obj["isCancelled"]?.jsonPrimitive?.booleanOrNull ?: false
            val since = obj["since"]?.jsonPrimitive?.contentOrNull
            val until = obj["until"]?.jsonPrimitive?.contentOrNull
            val updatedAt = obj["updatedAt"]?.jsonPrimitive?.contentOrNull

            val trainers = (obj["trainersList"] as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.get("person")?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull }
                ?: emptyList()

            val targetCohorts = (obj["targetCohortsList"] as? JsonArray)?.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val cohortIdPrim = o["cohortId"]?.jsonPrimitive
                val cohortId = cohortIdPrim?.longOrNull ?: cohortIdPrim?.contentOrNull?.toLongOrNull()
                val cohortObj = o["cohort"] as? JsonObject
                val cohort = cohortObj?.let { c ->
                    val cidPrim = c["id"]?.jsonPrimitive
                    val cid = cidPrim?.longOrNull ?: cidPrim?.contentOrNull?.toLongOrNull()
                    Cohort(cid, c["name"]?.jsonPrimitive?.contentOrNull, c["colorRgb"]?.jsonPrimitive?.contentOrNull)
                }
                TargetCohort(cohortId, cohort)
            } ?: emptyList()

            val registrations = (obj["eventInstanceRegistrationsByInstanceIdList"] as? JsonArray)
                ?.let { parseRegistrationsFromJson(it) } ?: emptyList()

            val locationObj = obj["location"] as? JsonObject
            val location = locationObj?.let { l ->
                val lidPrim = l["id"]?.jsonPrimitive
                val lid = lidPrim?.longOrNull ?: lidPrim?.contentOrNull?.toLongOrNull()
                Location(lid, l["name"]?.jsonPrimitive?.contentOrNull)
            }

            val event = Event(
                id = id,
                name = obj["name"]?.jsonPrimitive?.contentOrNull,
                description = obj["description"]?.jsonPrimitive?.contentOrNull,
                type = obj["type"]?.jsonPrimitive?.contentOrNull,
                locationText = obj["locationText"]?.jsonPrimitive?.contentOrNull,
                isRegistrationOpen = false,
                isVisible = obj["isVisible"]?.jsonPrimitive?.booleanOrNull ?: false,
                isPublic = obj["isPublic"]?.jsonPrimitive?.booleanOrNull ?: false,
                eventTrainersList = trainers,
                eventTargetCohortsList = targetCohorts,
                eventRegistrationsList = registrations,
                location = location
            )

            instances += EventInstance(id, isCancelled, since, until, updatedAt, event)
        }

        // Group by date string (yyyy-MM-dd) taken from `since` (left of 'T')
        val grouped = instances.groupBy { inst ->
            val s = inst.since ?: inst.until ?: inst.updatedAt ?: ""
            val datePart = s.substringBefore('T').ifEmpty { s }
            datePart.ifEmpty { "unknown" }
        }

        // Trigger notification processing only when fetching the current user's own events.
        // Calling processEvents with all events (onlyMine=false) would schedule notifications
        // for every participant, not just the current user.
        if (onlyMine) {
            try {
                val allInstances = instances.toList()
                try {
                    ServiceLocator.notificationService.processEvents(allInstances)
                } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    // ignore notification scheduling errors
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }

        val result = grouped.entries.sortedBy { it.key }.associate { it.key to it.value }
        try {
            Logger.d("EventService", "fetchEventsGroupedByDay: fetched ${instances.size} instances, storing ${result.size} grouped days into cache key=$cacheKey")
            cache.put(cacheKey, result, ttl = 3.minutes)
        } catch (e: CancellationException) { throw e } catch (t: Exception) {
            Logger.d("EventService", "fetchEventsGroupedByDay: cache.put failed: ${t.message}")
        }
        return result
    }

    override suspend fun fetchEventById(id: BigInt, forceRefresh: Boolean): JsonObject? {
        val cacheKey = "event_${id}"
        if (!forceRefresh) {
            cache.get<JsonObject>(cacheKey)?.let {
                Logger.d("EventService", "fetchEventById($id): cache HIT")
                return it
            }
        }

        val variables = buildJsonObject { put("id", JsonPrimitive(id)) }
        val resp = try {
            client.post(eventByIdQuery, variables)
        } catch (ex: Exception) {
            Logger.d("EventService", "fetchEventById($id): network exception: ${ex.message}")
            return null
        }

        val dataNode = resp.jsonObject["data"]
        val errorsNode = resp.jsonObject["errors"]
        Logger.d("EventService", "fetchEventById($id): raw response keys=${resp.jsonObject.keys}, data keys=${dataNode?.jsonObject?.keys}")
        val data = dataNode?.jsonObject ?: run {
            Logger.d("EventService", "fetchEventById($id): no 'data' in response, errors=$errorsNode")
            return null
        }
        val ev = data["eventInstance"]
        Logger.d("EventService", "fetchEventById($id): event value type=${ev?.let { it::class.simpleName }}, value=$ev")
        val obj = (ev as? JsonObject)
        if (obj != null) {
            try { cache.put(cacheKey, obj, ttl = 5.minutes) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
        return obj
    }

    private val setEventInstanceRegistrationMutation = """
        mutation SetEventInstanceRegistration(${'$'}input: SetEventInstanceRegistrationInput!) {
            setEventInstanceRegistration(input: ${'$'}input) {
                eventInstanceRegistration { id registrationStatus }
            }
        }
    """.trimIndent()

    override suspend fun setEventInstanceRegistration(
        instanceId: Long,
        personId: Long?,
        coupleId: Long?,
        isRegistered: Boolean,
        note: String?,
        lessonTrainerIds: List<Long>?,
        lessonCounts: List<Int>?
    ): JsonObject? {
        val variables = buildJsonObject {
            put("input", buildJsonObject {
                put("pInstanceId", JsonPrimitive(instanceId.toString()))
                put("pPersonId", if (personId != null) JsonPrimitive(personId.toString()) else JsonNull)
                put("pCoupleId", if (coupleId != null) JsonPrimitive(coupleId.toString()) else JsonNull)
                put("pIsRegistered", JsonPrimitive(isRegistered))
                if (note != null) put("pNote", JsonPrimitive(note))
                if (lessonTrainerIds != null && lessonCounts != null) {
                    put("pLessonTrainerIds", JsonArray(lessonTrainerIds.map { JsonPrimitive(it.toString()) }))
                    put("pLessonCounts", JsonArray(lessonCounts.map { JsonPrimitive(it) }))
                }
            })
        }

        val resp = try {
            client.post(setEventInstanceRegistrationMutation, variables)
        } catch (ex: Exception) {
            Logger.d("EventService", "setEventInstanceRegistration: network error: ${ex.message}")
            return null
        }

        val errors = resp.jsonObject["errors"]
        if (errors != null && errors !is JsonNull) {
            Logger.d("EventService", "setEventInstanceRegistration: GraphQL error: $errors")
            throw Exception(errors.toString())
        }

        val reg = resp.jsonObject["data"]?.jsonObject
            ?.get("setEventInstanceRegistration")?.jsonObject
            ?.get("eventInstanceRegistration") as? JsonObject

        if (reg != null) {
            try { cache.invalidatePrefix("calendar_") } catch (_: Exception) {}
            try { cache.invalidatePrefix("overview_") } catch (_: Exception) {}
            try { cache.invalidate("event_$instanceId") } catch (_: Exception) {}
        }
        return reg
    }

    override suspend fun setLessonDemand(registrationId: String, trainerId: Long, lessonCount: Int): Boolean {
        val mutation = """mutation SetLessonDemand(${'$'}input: SetLessonDemandInput!) { setLessonDemand(input: ${'$'}input) { eventLessonDemand { id } } }"""
        val variables = buildJsonObject {
            put("input", buildJsonObject {
                put("instanceRegistrationId", JsonPrimitive(registrationId))
                put("instanceTrainerId", JsonPrimitive(trainerId.toString()))
                put("lessonCount", JsonPrimitive(lessonCount))
            })
        }

        val resp = try {
            client.post(mutation, variables)
        } catch (ex: Exception) {
            return false
        }

        val errors = resp.jsonObject["errors"]
        if (errors != null && errors !is JsonNull) {
            Logger.d("EventService", "setLessonDemand: GraphQL error: $errors")
            return false
        }
        return resp.jsonObject["data"]?.jsonObject
            ?.get("setLessonDemand")?.jsonObject
            ?.get("eventLessonDemand") != null
    }
}
