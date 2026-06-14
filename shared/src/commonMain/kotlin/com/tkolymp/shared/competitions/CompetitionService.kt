package com.tkolymp.shared.competitions

import com.tkolymp.shared.Logger
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.network.IGraphQlClient
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private const val FIELD_BLOCK = """
    competitionId
    competitionDate
    checkInEnd
    competitionType
    isFinal
    hasResult
    eventName
    eventLocation
    eventId
    federation
    federatedPersonId
    personName
    personId
    competitorName
    competitorId
    competitorType
    dances
    participants
    ranking
    rankingTo
    pointGain
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

    private val briefQuery = """
        query CompetitionBrief(${'$'}first: Int, ${'$'}pSince: Date, ${'$'}pUntil: Date) {
            competitionBriefList(first: ${'$'}first, offset: 0, pCohortId: null, pPersonIds: null, pSince: ${'$'}pSince, pUntil: ${'$'}pUntil) {
                $FIELD_BLOCK
            }
        }
    """.trimIndent()

    private val reportQuery = """
        query CompetitionReport(${'$'}first: Int, ${'$'}pSince: Date, ${'$'}pUntil: Date) {
            competitionReportList(first: ${'$'}first, offset: 0, pCohortId: null, pPersonIds: null, pSince: ${'$'}pSince, pUntil: ${'$'}pUntil) {
                $FIELD_BLOCK
            }
        }
    """.trimIndent()

    override suspend fun getUpcomingCompetitions(
        pSince: String?,
        pUntil: String?,
        first: Int
    ): List<Competition> {
        val cacheKey = "competitions_upcoming_${pSince}_${pUntil}_$first"
        cache.get<List<Competition>>(cacheKey)?.let { return it }

        val vars = buildJsonObject {
            put("first", JsonPrimitive(first))
            if (pSince != null) put("pSince", JsonPrimitive(pSince))
            if (pUntil != null) put("pUntil", JsonPrimitive(pUntil))
        }
        val resp = client.post(briefQuery, vars)
        Logger.d("CompetitionService", "briefList raw response: $resp")
        val list = parseList(resp.jsonObject["data"]?.jsonObject?.get("competitionBriefList"))
            .sortedBy { it.competitionDate }
        try { cache.put(cacheKey, list, ttl = 5.minutes) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        return list
    }

    override suspend fun getPastCompetitions(
        pSince: String?,
        pUntil: String?,
        first: Int
    ): List<Competition> {
        val cacheKey = "competitions_past_${pSince}_${pUntil}_$first"
        cache.get<List<Competition>>(cacheKey)?.let { return it }

        val vars = buildJsonObject {
            put("first", JsonPrimitive(first))
            if (pSince != null) put("pSince", JsonPrimitive(pSince))
            if (pUntil != null) put("pUntil", JsonPrimitive(pUntil))
        }
        val resp = client.post(reportQuery, vars)
        Logger.d("CompetitionService", "reportList raw response: $resp")
        val list = parseList(resp.jsonObject["data"]?.jsonObject?.get("competitionReportList"))
            .sortedByDescending { it.competitionDate }
        try { cache.put(cacheKey, list, ttl = 10.minutes) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        return list
    }

    override suspend fun getNearestUpcoming(): Competition? {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        return try {
            getUpcomingCompetitions(pSince = today, first = 10).firstOrNull()
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
                Competition(
                    competitionId = obj["competitionId"]?.jsonPrimitive?.longOrNull
                        ?: obj["competitionId"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
                    competitionDate = date,
                    checkInEnd = obj["checkInEnd"]?.jsonPrimitive?.contentOrNull,
                    competitionType = obj["competitionType"]?.jsonPrimitive?.contentOrNull,
                    isFinal = obj["isFinal"]?.jsonPrimitive?.booleanOrNull ?: false,
                    hasResult = obj["hasResult"]?.jsonPrimitive?.booleanOrNull ?: false,
                    eventName = obj["eventName"]?.jsonPrimitive?.contentOrNull,
                    eventLocation = obj["eventLocation"]?.jsonPrimitive?.contentOrNull,
                    eventId = obj["eventId"]?.jsonPrimitive?.longOrNull
                        ?: obj["eventId"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
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
