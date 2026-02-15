package com.tkolymp.shared.people

import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.cache.CacheService
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import com.tkolymp.shared.network.IGraphQlClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class PersonName(val firstName: String?, val lastName: String?)

data class Cohort(val id: String?, val name: String?, val colorRgb: String?, val isVisible: Boolean?)
data class CohortMembership(val cohort: Cohort?, val since: String? = null, val until: String? = null)
data class Person(
    val id: String,
    val firstName: String?,
    val lastName: String?,
    val prefixTitle: String? = null,
    val suffixTitle: String? = null,
    val birthDate: String?,
    val cohortMembershipsList: List<CohortMembership>
)

data class CoupleMember(val firstName: String?, val lastName: String?)
data class ActiveCouple(val id: String?, val man: CoupleMember?, val woman: CoupleMember?)

data class PersonDetails(
    val id: String,
    val firstName: String?,
    val lastName: String?,
    val prefixTitle: String? = null,
    val suffixTitle: String? = null,
    val birthDate: String?,
    val bio: String?,
    val cstsId: String?,
    val email: String?,
    val gender: String?,
    val isTrainer: Boolean?,
    val phone: String?,
    val wdsfId: String?,
    val activeCouplesList: List<ActiveCouple>,
    val cohortMembershipsList: List<CohortMembership>,
    val rawResponse: JsonElement? = null
)

data class ScoreboardEntry(
    val ranking: Int?,
    val personId: String?,
    val personFirstName: String?,
    val personLastName: String?,
    val totalScore: Double? = null,
    val lessonTotalScore: Double? = null,
    val groupTotalScore: Double? = null,
    val eventTotalScore: Double? = null,
    val manualTotalScore: Double? = null
)

class PeopleService(private val client: IGraphQlClient = ServiceLocator.graphQlClient,
                    private val cache: CacheService = ServiceLocator.cacheService) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchPersonName(personId: String): PersonName? {
        val query = """
            query MyQuery {
              person(id: "$personId") {
                lastName
                firstName
              }
            }
        """.trimIndent()

        val el: JsonElement = try { client.post(query) } catch (_: Exception) { return null }
        val personObj = (el as? JsonObject)
            ?.get("data")?.let { it as? JsonObject }
            ?.get("person")?.let { it as? JsonObject }

        val first = personObj?.get("firstName")?.jsonPrimitive?.contentOrNull
        val last = personObj?.get("lastName")?.jsonPrimitive?.contentOrNull
        return if (first == null && last == null) null else PersonName(first, last)
    }

    suspend fun fetchPersonDisplayName(personId: String, markMe: Boolean = false): String? {
        val p = fetchPersonName(personId) ?: return null
        val name = listOfNotNull(p.firstName, p.lastName).joinToString(" ")
        return if (name.isBlank()) null else if (markMe) "$name (já)" else name
    }

    suspend fun fetchCoupleDisplayName(coupleId: String): String? {
        val query = """
            query MyQuery {
              couple(id: "$coupleId") {
                man { lastName firstName }
                woman { firstName lastName }
              }
            }
        """.trimIndent()

        val el: JsonElement = try { client.post(query) } catch (_: Exception) { return null }
        val coupleObj = (el as? JsonObject)
            ?.get("data")?.let { it as? JsonObject }
            ?.get("couple")?.let { it as? JsonObject }

        val manObj = coupleObj?.get("man") as? JsonObject
        val womanObj = coupleObj?.get("woman") as? JsonObject

        val manLast = manObj?.get("lastName")?.jsonPrimitive?.contentOrNull
        val womanLast = womanObj?.get("lastName")?.jsonPrimitive?.contentOrNull

        return when {
            !manLast.isNullOrBlank() && !womanLast.isNullOrBlank() -> "$manLast - $womanLast"
            else -> {
                val manName = listOfNotNull(manObj?.get("firstName")?.jsonPrimitive?.contentOrNull, manLast).joinToString(" ").trim()
                val womanName = listOfNotNull(womanObj?.get("firstName")?.jsonPrimitive?.contentOrNull, womanLast).joinToString(" ").trim()
                val parts = listOfNotNull(manName.takeIf { it.isNotBlank() }, womanName.takeIf { it.isNotBlank() })
                if (parts.isEmpty()) null else parts.joinToString(" - ")
            }
        }
    }

    suspend fun fetchPeople(): List<Person> {
        val cacheKey = "people_all"
        try {
            val cached: List<Person>? = cache.get(cacheKey)
            if (cached != null) return cached
        } catch (_: Throwable) {}
        val query = """
            query MyQuery {
                people {
                    nodes {
                        id
                        firstName
                        lastName
                        prefixTitle
                        suffixTitle
                        birthDate
                        cohortMembershipsList {
                            cohort {
                                name
                                id
                                colorRgb
                                isVisible
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val el = try { client.post(query) } catch (_: Exception) { return emptyList() }
        val nodes = (el as? JsonObject)
            ?.get("data")?.let { it as? JsonObject }
            ?.get("people")?.let { it as? JsonObject }
            ?.get("nodes")?.let { it as? kotlinx.serialization.json.JsonArray }
            ?: return emptyList()

        val result = nodes.mapNotNull { nodeEl ->
            val node = nodeEl as? JsonObject ?: return@mapNotNull null
            val id = node.get("id")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val first = node.get("firstName")?.jsonPrimitive?.contentOrNull
            val last = node.get("lastName")?.jsonPrimitive?.contentOrNull
            val prefix = node.get("prefixTitle")?.jsonPrimitive?.contentOrNull
            val suffix = node.get("suffixTitle")?.jsonPrimitive?.contentOrNull
            val birth = node.get("birthDate")?.jsonPrimitive?.contentOrNull

            val memberships = (node.get("cohortMembershipsList") as? kotlinx.serialization.json.JsonArray)?.mapNotNull { mEl ->
                val mObj = mEl as? JsonObject ?: return@mapNotNull null
                val cohortObj = mObj.get("cohort") as? JsonObject
                val cId = cohortObj?.get("id")?.jsonPrimitive?.contentOrNull
                val cName = cohortObj?.get("name")?.jsonPrimitive?.contentOrNull
                val cColor = cohortObj?.get("colorRgb")?.jsonPrimitive?.contentOrNull
                val cVis = cohortObj?.get("isVisible")?.jsonPrimitive?.contentOrNull?.let { it == "true" }
                CohortMembership(Cohort(cId, cName, cColor, cVis))
            } ?: emptyList()

            Person(id, first, last, prefix, suffix, birth, memberships)
        }
        try { cache.put(cacheKey, result, ttl = 10.minutes) } catch (_: Throwable) {}
        return result
    }

        suspend fun fetchPerson(personId: String): PersonDetails? {
            val cacheKey = "person_$personId"
            try {
                val cached: PersonDetails? = cache.get(cacheKey)
                if (cached != null) return cached
            } catch (_: Throwable) {}
                // use GraphQL variable for person id and pass it via `variables` to the client
                val query = """
                        query PersonBasic(${'$'}id: BigInt!) {
                            person(id: ${'$'}id) {
                                id
                                bio
                                birthDate
                                cstsId
                                email
                                firstName
                                prefixTitle
                                suffixTitle
                                gender
                                isTrainer
                                lastName
                                phone
                                wdsfId
                                activeCouplesList {
                                    man { firstName lastName }
                                    woman { firstName lastName }
                                    id
                                }
                                cohortMembershipsList {
                                    cohort { colorRgb name isVisible id }
                                    since
                                    until
                                }
                            }
                        }
                """.trimIndent()

                val idVar = personId.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(personId)
                val variables = buildJsonObject { put("id", idVar) }
                val el = try { client.post(query, variables) } catch (ex: Exception) {
                    // surface exception for callers/logging
                    throw ex
                }
            val root = (el as? JsonObject)?.get("data") as? JsonObject
            if (root == null) {
                    // return an object with the raw response so UI can show it for debugging
                    return PersonDetails(
                        id = personId,
                        firstName = null,
                        lastName = null,
                        prefixTitle = null,
                        suffixTitle = null,
                        birthDate = null,
                        bio = null,
                        cstsId = null,
                        email = null,
                        gender = null,
                        isTrainer = null,
                        phone = null,
                        wdsfId = null,
                        activeCouplesList = emptyList(),
                        cohortMembershipsList = emptyList(),
                        rawResponse = el
                    )
                }

            val personObj = root.get("person") as? JsonObject
            val id = personObj?.get("id")?.jsonPrimitive?.contentOrNull ?: personId
                val first = personObj?.get("firstName")?.jsonPrimitive?.contentOrNull
                val last = personObj?.get("lastName")?.jsonPrimitive?.contentOrNull
                val prefix = personObj?.get("prefixTitle")?.jsonPrimitive?.contentOrNull
                val suffix = personObj?.get("suffixTitle")?.jsonPrimitive?.contentOrNull
                val birth = personObj?.get("birthDate")?.jsonPrimitive?.contentOrNull
                val bio = personObj?.get("bio")?.jsonPrimitive?.contentOrNull
                val csts = personObj?.get("cstsId")?.jsonPrimitive?.contentOrNull
                val email = personObj?.get("email")?.jsonPrimitive?.contentOrNull
                val gender = personObj?.get("gender")?.jsonPrimitive?.contentOrNull
                val isTrainer = personObj?.get("isTrainer")?.jsonPrimitive?.contentOrNull?.let { it == "true" }
                val phone = personObj?.get("phone")?.jsonPrimitive?.contentOrNull
                val wdsf = personObj?.get("wdsfId")?.jsonPrimitive?.contentOrNull

            val couplesArr = (personObj?.get("activeCouplesList") as? kotlinx.serialization.json.JsonArray)?._safeMap { cEl ->
                        val cObj = cEl as? JsonObject ?: return@_safeMap null
                        val cid = cObj.get("id")?.jsonPrimitive?.contentOrNull
                        val manObj = cObj.get("man") as? JsonObject
                        val womanObj = cObj.get("woman") as? JsonObject
                        val man = CoupleMember(manObj?.get("firstName")?.jsonPrimitive?.contentOrNull, manObj?.get("lastName")?.jsonPrimitive?.contentOrNull)
                        val woman = CoupleMember(womanObj?.get("firstName")?.jsonPrimitive?.contentOrNull, womanObj?.get("lastName")?.jsonPrimitive?.contentOrNull)
                        ActiveCouple(cid, man, woman)
            } ?: emptyList()

            val memberships = (personObj?.get("cohortMembershipsList") as? kotlinx.serialization.json.JsonArray)?._safeMap { mEl ->
                val mObj = mEl as? JsonObject ?: return@_safeMap null
                val cohortObj = mObj.get("cohort") as? JsonObject
                val cId = cohortObj?.get("id")?.jsonPrimitive?.contentOrNull
                val cName = cohortObj?.get("name")?.jsonPrimitive?.contentOrNull
                val cColor = cohortObj?.get("colorRgb")?.jsonPrimitive?.contentOrNull
                val cVis = cohortObj?.get("isVisible")?.jsonPrimitive?.contentOrNull?.let { it == "true" }
                val since = mObj.get("since")?.jsonPrimitive?.contentOrNull
                val until = mObj.get("until")?.jsonPrimitive?.contentOrNull
                CohortMembership(Cohort(cId, cName, cColor, cVis), since, until)
            } ?: emptyList()

            val pd = PersonDetails(id, first, last, prefix, suffix, birth, bio, csts, email, gender, isTrainer, phone, wdsf, couplesArr, memberships, el)
            try { cache.put(cacheKey, pd, ttl = 15.minutes) } catch (_: Throwable) {}
            return pd
        }

        suspend fun fetchScoreboard(cohortId: String? = null, since: String, until: String): List<ScoreboardEntry> {
            val keyPart = cohortId ?: "all"
            val cacheKey = "scoreboard_${keyPart}_${since}_${until}"
            try {
                val cached: List<ScoreboardEntry>? = cache.get(cacheKey)
                if (cached != null) return cached
            } catch (_: Throwable) {}
            val query = """
                    query Scoreboard(${'$'}cohortId: BigInt, ${'$'}since: Date, ${'$'}until: Date) {
                        scoreboardEntriesList(cohortId: ${'$'}cohortId, since: ${'$'}since, until: ${'$'}until) {
                            ranking
                            personId
                            totalScore
                            lessonTotalScore
                            groupTotalScore
                            eventTotalScore
                            manualTotalScore
                            person { firstName lastName id }
                        }
                    }
            """.trimIndent()

            val idVar = cohortId?.toLongOrNull()?.let { JsonPrimitive(it) } ?: cohortId?.let { JsonPrimitive(it) }
            val variables = buildJsonObject {
                if (idVar != null) put("cohortId", idVar)
                put("since", JsonPrimitive(since))
                put("until", JsonPrimitive(until))
            }

            val el = try { client.post(query, variables) } catch (_: Exception) { return emptyList() }
            val arr = (el as? JsonObject)
                ?.get("data")?.let { it as? JsonObject }
                ?.get("scoreboardEntriesList") as? kotlinx.serialization.json.JsonArray
                ?: return emptyList()

            val res = arr.mapNotNull { e ->
                val obj = e as? JsonObject ?: return@mapNotNull null
                val ranking = obj.get("ranking")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                val personId = obj.get("personId")?.jsonPrimitive?.contentOrNull
                val total = obj.get("totalScore")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                val lesson = obj.get("lessonTotalScore")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                val group = obj.get("groupTotalScore")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                val event = obj.get("eventTotalScore")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                val manual = obj.get("manualTotalScore")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                val personObj = obj.get("person") as? JsonObject
                val first = personObj?.get("firstName")?.jsonPrimitive?.contentOrNull
                val last = personObj?.get("lastName")?.jsonPrimitive?.contentOrNull
                ScoreboardEntry(ranking, personId, first, last, total, lesson, group, event, manual)
            }
            try { cache.put(cacheKey, res, ttl = 15.minutes) } catch (_: Throwable) {}
            return res
        }

        // helper extension to map JsonArray safely to list (generic)
        private fun <T> kotlinx.serialization.json.JsonArray._safeMap(mapper: (kotlinx.serialization.json.JsonElement) -> T?): List<T> {
            return this.mapNotNull { mapper(it) }
        }
}
