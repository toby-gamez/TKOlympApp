package com.tkolymp.shared.event

import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.network.IGraphQlClient
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
        onlyType: String? = null
    ): Map<String, List<EventInstance>>
}

// Notes: GraphQL types
// - `id` is BigInt (use `BigInt` alias)
// - `since`, `until`, `updatedAt` are Datetime (use `DateTime` alias)
typealias BigInt = Long
typealias DateTime = String

data class EventInstance(
    val id: BigInt,
    val isCancelled: Boolean,
    val since: DateTime?,
    val until: DateTime?,
    val updatedAt: DateTime?,
    val event: Event?
)

data class Cohort(val id: BigInt?, val name: String?, val colorRgb: String?)

data class TargetCohort(val cohortId: BigInt?, val cohort: Cohort?)

data class Person(val id: BigInt?, val name: String?, val firstName: String?, val lastName: String?)

data class SimpleName(val firstName: String?, val lastName: String?)

data class Couple(val id: BigInt?, val man: SimpleName?, val woman: SimpleName?)

data class Registration(val id: BigInt?, val person: Person?, val couple: Couple?)

data class Location(val id: BigInt?, val name: String?)

data class Event(
    val id: BigInt?,
    val name: String?,
    val description: String?,
    val type: String?,
    val locationText: String?,
    val isRegistrationOpen: Boolean,
    val isVisible: Boolean,
    val isPublic: Boolean,
    val eventTrainersList: List<String>,
    val eventTargetCohortsList: List<TargetCohort>,
    val eventRegistrationsList: List<Registration>,
    val location: Location?
)

class EventService(private val client: IGraphQlClient = ServiceLocator.graphQlClient) : IEventService {
    private val json = Json { ignoreUnknownKeys = true }

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
                        event {
                            id
                            description
                            name
                            type
                            locationText
                            isRegistrationOpen
                            isVisible
                            isPublic
                            eventTrainersList { name }
                            eventTargetCohortsList { cohortId cohort { id name colorRgb } }
                            eventRegistrationsList {
                                id
                                person { id name firstName lastName }
                                couple { id man { firstName lastName } woman { firstName lastName } }
                            }
                            location { id name }
                        }
                    }
                }
        """.trimIndent()

    override suspend fun fetchEventsGroupedByDay(
        startRangeIso: String,
        endRangeIso: String,
        onlyMine: Boolean,
        first: Int,
        offset: Int,
        onlyType: String?
    ): Map<String, List<EventInstance>> {
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
            return emptyMap()
        }

        val instances = mutableListOf<EventInstance>()

        val data = resp.jsonObject["data"]?.jsonObject
        val listElem = data?.get("eventInstancesForRangeList") ?: return emptyMap()

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

            val eventObj = obj["event"]?.jsonObject
            val event = eventObj?.let { e ->
                // parse id for event (may be BigInt as string)
                val evIdPrim = e["id"]?.jsonPrimitive
                val evId = evIdPrim?.longOrNull ?: evIdPrim?.contentOrNull?.toLongOrNull()

                val trainers = (e["eventTrainersList"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.mapNotNull { it["name"]?.jsonPrimitive?.contentOrNull } ?: emptyList()

                val targetCohorts = (e["eventTargetCohortsList"] as? JsonArray)?.mapNotNull { item ->
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

                val registrations = (e["eventRegistrationsList"] as? JsonArray)?.mapNotNull { item ->
                    val o = item as? JsonObject ?: return@mapNotNull null
                    val ridPrim = o["id"]?.jsonPrimitive
                    val rid = ridPrim?.longOrNull ?: ridPrim?.contentOrNull?.toLongOrNull()

                    val personObj = o["person"] as? JsonObject
                    val person = personObj?.let { p ->
                        val pidPrim = p["id"]?.jsonPrimitive
                        val pid = pidPrim?.longOrNull ?: pidPrim?.contentOrNull?.toLongOrNull()
                        Person(pid, p["name"]?.jsonPrimitive?.contentOrNull, p["firstName"]?.jsonPrimitive?.contentOrNull, p["lastName"]?.jsonPrimitive?.contentOrNull)
                    }

                    val coupleObj = o["couple"] as? JsonObject
                    val couple = coupleObj?.let { c ->
                        val cidPrim = c["id"]?.jsonPrimitive
                        val cid = cidPrim?.longOrNull ?: cidPrim?.contentOrNull?.toLongOrNull()
                        val manObj = c["man"] as? JsonObject
                        val womanObj = c["woman"] as? JsonObject
                        val man = manObj?.let { m -> SimpleName(m["firstName"]?.jsonPrimitive?.contentOrNull, m["lastName"]?.jsonPrimitive?.contentOrNull) }
                        val woman = womanObj?.let { w -> SimpleName(w["firstName"]?.jsonPrimitive?.contentOrNull, w["lastName"]?.jsonPrimitive?.contentOrNull) }
                        Couple(cid, man, woman)
                    }

                    Registration(rid, person, couple)
                } ?: emptyList()

                val locationObj = e["location"] as? JsonObject
                val location = locationObj?.let { l ->
                    val lidPrim = l["id"]?.jsonPrimitive
                    val lid = lidPrim?.longOrNull ?: lidPrim?.contentOrNull?.toLongOrNull()
                    Location(lid, l["name"]?.jsonPrimitive?.contentOrNull)
                }

                Event(
                    id = evId,
                    name = e["name"]?.jsonPrimitive?.contentOrNull,
                    description = e["description"]?.jsonPrimitive?.contentOrNull,
                    type = e["type"]?.jsonPrimitive?.contentOrNull,
                    locationText = e["locationText"]?.jsonPrimitive?.contentOrNull,
                    isRegistrationOpen = e["isRegistrationOpen"]?.jsonPrimitive?.booleanOrNull ?: false,
                    isVisible = e["isVisible"]?.jsonPrimitive?.booleanOrNull ?: false,
                    isPublic = e["isPublic"]?.jsonPrimitive?.booleanOrNull ?: false,
                    eventTrainersList = trainers,
                    eventTargetCohortsList = targetCohorts,
                    eventRegistrationsList = registrations,
                    location = location
                )
            }

            instances += EventInstance(id, isCancelled, since, until, updatedAt, event)
        }

        // Group by date string (yyyy-MM-dd) taken from `since` (left of 'T')
        val grouped = instances.groupBy { inst ->
            val s = inst.since ?: inst.until ?: inst.updatedAt ?: ""
            val datePart = s.substringBefore('T').ifEmpty { s }
            datePart.ifEmpty { "unknown" }
        }

        return grouped.toSortedMap()
    }
}
