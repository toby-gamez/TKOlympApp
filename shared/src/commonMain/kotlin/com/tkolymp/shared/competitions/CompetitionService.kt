package com.tkolymp.shared.competitions

import com.tkolymp.shared.Logger
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.network.IGraphQlClient
import com.tkolymp.shared.sync.OfflineSyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

// The old `competitionBriefList`/`competitionReportList` queries were removed server-side
// (the backing SQL functions are `@omit`); this data is now served through the generic
// `activityTimelineList` query filtered by `pKinds`, see
// https://github.com/zarybnicky/Sirimbo/blob/master/schema/functions/public.activity_timeline(timestamptz,%20timestamptz,%20bigint[],%20bigint,%20activity_timeline_kind[],%20event_type[]).sql
private const val CATEGORY_FIELD_BLOCK = """
    category {
        id
        name
        series
        discipline
        ageGroup
        genderGroup
        class
        competitorType
        baseDanceProgramId
    }
"""

class CompetitionService(
    private val client: IGraphQlClient = ServiceLocator.graphQlClient,
    private val cache: CacheService = ServiceLocator.cacheService
) : ICompetitionService {
    private val offlineSyncManager: OfflineSyncManager?
        get() = try { ServiceLocator.offlineSyncManager } catch (_: Exception) { null }

    private val timelineQuery = """
        query CompetitionTimeline(${'$'}first: Int, ${'$'}pSince: Datetime, ${'$'}pUntil: Datetime, ${'$'}pPersonIds: [BigInt], ${'$'}pKinds: [ActivityTimelineKind]) {
            activityTimelineList(first: ${'$'}first, offset: 0, pSince: ${'$'}pSince, pUntil: ${'$'}pUntil, pPersonIds: ${'$'}pPersonIds, pCohortId: null, pKinds: ${'$'}pKinds) {
                kind
                personId
                personName
                ... on ActivityCompetitionBrief {
                    federation
                    federatedPersonId
                    competitorId
                    competitorName
                    competitorType
                    competitionId
                    competitionDate
                    competitionType
                    checkInEnd
                    competitionEventId
                    competitionEventName
                    competitionEventLocation
                    dances
                    participants
                    $CATEGORY_FIELD_BLOCK
                }
                ... on ActivityCompetitionResult {
                    federation
                    federatedPersonId
                    competitorId
                    competitorName
                    competitorType
                    competitionId
                    competitionDate
                    competitionType
                    competitionEventId
                    competitionEventName
                    competitionEventLocation
                    dances
                    participants
                    ranking
                    rankingTo
                    pointGain
                    isFinal
                    $CATEGORY_FIELD_BLOCK
                }
            }
        }
    """.trimIndent()

    private fun toDatetime(date: String): String = "${date}T00:00:00Z"

    override suspend fun getUpcomingCompetitions(
        pSince: String?,
        pUntil: String?,
        first: Int,
        pPersonIds: List<Long>?
    ): List<Competition> {
        val personKey = pPersonIds?.joinToString(",") ?: "all"
        val cacheKey = "competitions_upcoming_${pSince}_${pUntil}_${first}_$personKey"
        cache.get<List<Competition>>(cacheKey)?.let { return it }

        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val since = pSince ?: today.toString()
        val until = pUntil ?: today.plus(1, DateTimeUnit.YEAR).toString()
        val vars = buildJsonObject {
            put("first", JsonPrimitive(first))
            put("pSince", JsonPrimitive(toDatetime(since)))
            put("pUntil", JsonPrimitive(toDatetime(until)))
            put("pKinds", JsonArray(listOf(JsonPrimitive("COMPETITION_BRIEF"))))
            if (pPersonIds != null) put("pPersonIds", JsonArray(pPersonIds.map { JsonPrimitive(it) }))
        }
        val list = try {
            val resp = client.post(timelineQuery, vars)
            Logger.d("CompetitionService", "activityTimelineList (brief) raw response: $resp")
            parseList(resp.jsonObject["data"]?.jsonObject?.get("activityTimelineList"))
                .sortedBy { it.competitionDate }
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            offlineSyncManager?.loadCompetitions()?.let { offline ->
                offline.filter { c ->
                    (c.competitionDate >= since) &&
                    (c.competitionDate <= until)
                }.sortedBy { it.competitionDate }.take(first)
            } ?: emptyList()
        }
        try { cache.put(cacheKey, list, ttl = 5.minutes) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        return list
    }

    override suspend fun getPastCompetitions(
        pSince: String?,
        pUntil: String?,
        first: Int,
        pPersonIds: List<Long>?
    ): List<Competition> {
        val personKey = pPersonIds?.joinToString(",") ?: "all"
        val cacheKey = "competitions_past_${pSince}_${pUntil}_${first}_$personKey"
        cache.get<List<Competition>>(cacheKey)?.let { return it }

        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val since = pSince ?: today.minus(1, DateTimeUnit.YEAR).toString()
        val until = pUntil ?: today.toString()
        val vars = buildJsonObject {
            put("first", JsonPrimitive(first))
            put("pSince", JsonPrimitive(toDatetime(since)))
            put("pUntil", JsonPrimitive(toDatetime(until)))
            put("pKinds", JsonArray(listOf(JsonPrimitive("COMPETITION_RESULT"))))
            if (pPersonIds != null) put("pPersonIds", JsonArray(pPersonIds.map { JsonPrimitive(it) }))
        }
        val resp = client.post(timelineQuery, vars)
        Logger.d("CompetitionService", "activityTimelineList (result) raw response: $resp")
        val list = parseList(resp.jsonObject["data"]?.jsonObject?.get("activityTimelineList"))
            .sortedByDescending { it.competitionDate }
        try { cache.put(cacheKey, list, ttl = 10.minutes) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        return list
    }

    override suspend fun getNearestUpcoming(pPersonIds: List<Long>?): Competition? {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        return try {
            getUpcomingCompetitions(pSince = today, first = 10, pPersonIds = pPersonIds).firstOrNull()
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            Logger.d("CompetitionService", "getNearestUpcoming failed: ${e.message}")
            null
        }
    }

    private fun parseList(element: JsonElement?): List<Competition> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { elem ->
            try {
                val obj = elem.jsonObject
                val date = obj["competitionDate"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val kind = obj["kind"]?.jsonPrimitive?.contentOrNull
                Competition(
                    competitionId = obj["competitionId"]?.jsonPrimitive?.longOrNull
                        ?: obj["competitionId"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
                    competitionDate = date,
                    checkInEnd = obj["checkInEnd"]?.jsonPrimitive?.contentOrNull,
                    competitionType = obj["competitionType"]?.jsonPrimitive?.contentOrNull,
                    isFinal = obj["isFinal"]?.jsonPrimitive?.booleanOrNull ?: false,
                    hasResult = kind == "COMPETITION_RESULT",
                    eventName = obj["competitionEventName"]?.jsonPrimitive?.contentOrNull,
                    eventLocation = obj["competitionEventLocation"]?.jsonPrimitive?.contentOrNull,
                    eventId = obj["competitionEventId"]?.jsonPrimitive?.longOrNull
                        ?: obj["competitionEventId"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
                    federation = obj["federation"]?.jsonPrimitive?.contentOrNull,
                    federatedPersonId = obj["federatedPersonId"]?.jsonPrimitive?.contentOrNull,
                    personName = obj["personName"]?.jsonPrimitive?.contentOrNull,
                    personId = obj["personId"]?.jsonPrimitive?.longOrNull
                        ?: obj["personId"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
                    competitorName = obj["competitorName"]?.jsonPrimitive?.contentOrNull,
                    competitorId = obj["competitorId"]?.jsonPrimitive?.contentOrNull,
                    competitorType = obj["competitorType"]?.jsonPrimitive?.contentOrNull,
                    dances = (obj["dances"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                    participants = obj["participants"]?.jsonPrimitive?.intOrNull,
                    ranking = obj["ranking"]?.jsonPrimitive?.intOrNull,
                    rankingTo = obj["rankingTo"]?.jsonPrimitive?.intOrNull,
                    pointGain = obj["pointGain"]?.jsonPrimitive?.contentOrNull,
                    category = obj["category"]?.jsonObject?.let { cat ->
                        CompetitionCategory(
                            id = cat["id"]?.jsonPrimitive?.longOrNull ?: cat["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
                            name = cat["name"]?.jsonPrimitive?.contentOrNull,
                            series = cat["series"]?.jsonPrimitive?.contentOrNull,
                            discipline = cat["discipline"]?.jsonPrimitive?.contentOrNull,
                            ageGroup = cat["ageGroup"]?.jsonPrimitive?.contentOrNull,
                            genderGroup = cat["genderGroup"]?.jsonPrimitive?.contentOrNull,
                            competitorClass = cat["class"]?.jsonPrimitive?.contentOrNull,
                            competitorType = cat["competitorType"]?.jsonPrimitive?.contentOrNull,
                            baseDanceProgramId = cat["baseDanceProgramId"]?.jsonPrimitive?.longOrNull
                                ?: cat["baseDanceProgramId"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                        )
                    }
                )
            } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        }
    }
}
