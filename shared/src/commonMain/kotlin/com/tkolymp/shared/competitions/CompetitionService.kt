package com.tkolymp.shared.competitions

import com.tkolymp.shared.graphql.GraphQlOperations
import com.tkolymp.shared.Logger
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.network.IGraphQlClient
import com.tkolymp.shared.sync.OfflineSyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

// The old `competitionBriefList`/`competitionReportList` queries and the generic
// `activityTimelineList` that replaced them are going away server-side. Competitions are now
// read from the relational types: Person -> federationPeople -> competitorComponents gives the
// person's competitors (a couple and any teams), and Person.federationCompetitions gives the
// competitions they entered, each with its entries and results.
//
// There is no club-wide query (`competitions` spans the whole federation and the computed
// `federationCompetitions` field times out when asked for many people at once), so "all"
// (pPersonIds == null) is resolved to the club's people first and fetched one person at a time.

class CompetitionService(
    private val client: IGraphQlClient = ServiceLocator.graphQlClient,
    private val cache: CacheService = ServiceLocator.cacheService
) : ICompetitionService {
    private val offlineSyncManager: OfflineSyncManager?
        get() = try { ServiceLocator.offlineSyncManager } catch (_: Exception) { null }

    private val personQuery = GraphQlOperations.PersonCompetitions
    private val clubPeopleQuery = GraphQlOperations.CompetitionClubPeople

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
        val list = try {
            fetch(pPersonIds, since, until, upcoming = true)
                .distinctBy { dedupeKey(it) }
                .sortedBy { it.competitionDate }
                .take(first)
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            Logger.d("CompetitionService", "upcoming fetch failed: ${e.message}")
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
        val list = try {
            fetch(pPersonIds, since, until, upcoming = false)
                .distinctBy { dedupeKey(it) }
                .sortedByDescending { it.competitionDate }
                .take(first)
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            Logger.d("CompetitionService", "past fetch failed: ${e.message}")
            offlineSyncManager?.loadPastCompetitions()?.let { offline ->
                offline.filter { c ->
                    (c.competitionDate >= since) &&
                    (c.competitionDate <= until)
                }.sortedByDescending { it.competitionDate }.take(first)
            } ?: emptyList()
        }
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

    // A competition is reached once per competitor of the same person (couple + team entries)
    // and once per person when several club members share it, so identical rows are collapsed.
    private fun dedupeKey(c: Competition): List<Any?> = listOf(
        c.competitionId,
        c.competitorId,
        c.category?.id,
        c.hasResult
    )

    private suspend fun fetch(
        pPersonIds: List<Long>?,
        since: String,
        until: String,
        upcoming: Boolean
    ): List<Competition> {
        val ids = pPersonIds ?: clubPersonIds()
        if (ids.isEmpty()) return emptyList()
        // Server-side the per-person query is slow (~1-2 s), so keep a few in flight at a time.
        val gate = Semaphore(PERSON_CONCURRENCY)
        return coroutineScope {
            ids.map { id -> async { gate.withPermit { fetchPerson(id, since, until, upcoming) } } }
                .awaitAll()
                .flatten()
        }
    }

    // Only people with at least one federation competitor can have competitions.
    private suspend fun clubPersonIds(): List<Long> {
        val resp = client.post(clubPeopleQuery)
        val nodes = resp.jsonObject["data"]?.jsonObject?.get("people")?.jsonObject?.get("nodes") as? JsonArray
            ?: return emptyList()
        return nodes.mapNotNull { n ->
            val obj = n.jsonObject
            val hasCompetitor = (obj["federationPeople"]?.jsonObject?.get("nodes") as? JsonArray)?.any { fp ->
                (fp.jsonObject["competitorComponents"]?.jsonObject?.get("totalCount")?.jsonPrimitive?.intOrNull ?: 0) > 0
            } ?: false
            if (hasCompetitor) obj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() else null
        }
    }

    // `federationCompetitions` is ordered newest-first with no date-range argument, so page
    // until a page reaches back past `since`.
    private suspend fun fetchPerson(personId: Long, since: String, until: String, upcoming: Boolean): List<Competition> {
        val out = mutableListOf<Competition>()
        var offset = 0
        // Newest-first, so upcoming competitions sit on the first page; keep it small there.
        val pageSize = if (upcoming) UPCOMING_PAGE_SIZE else PAGE_SIZE
        repeat(MAX_PAGES) {
            val vars = buildJsonObject {
                put("personId", JsonPrimitive(personId.toString()))
                put("first", JsonPrimitive(pageSize))
                put("offset", JsonPrimitive(offset))
            }
            val resp = client.post(personQuery, vars)
            Logger.d("CompetitionService", "PersonCompetitions($personId, offset=$offset) response: ${resp.toString().length} chars")
            val person = (resp.jsonObject["data"]?.jsonObject?.get("people")?.jsonObject?.get("nodes") as? JsonArray)
                ?.firstOrNull()?.jsonObject ?: return out
            val competitions = (person["federationCompetitions"]?.jsonObject?.get("nodes") as? JsonArray).orEmpty()
            out += parsePerson(person, competitions, since, until, upcoming)
            val oldest = competitions.lastOrNull()?.jsonObject?.get("startDate")?.jsonPrimitive?.contentOrNull
            if (competitions.size < pageSize || (oldest != null && oldest < since)) return out
            offset += pageSize
        }
        return out
    }

    private class CompetitorInfo(val name: String?, val type: String?)

    private fun parsePerson(
        person: JsonObject,
        competitions: List<JsonElement>,
        since: String,
        until: String,
        upcoming: Boolean
    ): List<Competition> {
        val personId = person["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        val personName = person["name"]?.jsonPrimitive?.contentOrNull

        val competitors = mutableMapOf<String, CompetitorInfo>()
        var federatedPersonId: String? = null
        (person["federationPeople"]?.jsonObject?.get("nodes") as? JsonArray)?.forEach { fp ->
            val fpObj = fp.jsonObject
            if (federatedPersonId == null) federatedPersonId = fpObj["externalId"]?.jsonPrimitive?.contentOrNull
            (fpObj["competitorComponents"]?.jsonObject?.get("nodes") as? JsonArray)?.forEach { comp ->
                val c = comp.jsonObject["competitor"]?.jsonObject ?: return@forEach
                val id = c["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                // Couples have an empty name server-side; show the partners instead.
                val name = c["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: (c["peopleList"] as? JsonArray)
                        ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                        ?.joinToString(" - ")?.takeIf { it.isNotBlank() }
                competitors[id] = CompetitorInfo(name, c["competitorType"]?.jsonPrimitive?.contentOrNull)
            }
        }

        val result = mutableListOf<Competition>()
        for (elem in competitions) {
            val obj = elem.jsonObject
            val date = obj["startDate"]?.jsonPrimitive?.contentOrNull ?: continue
            if (date < since || date > until) continue
            val results = (obj["resultsList"] as? JsonArray).orEmpty()
                .map { it.jsonObject }
                .filter { it["competitorId"]?.jsonPrimitive?.contentOrNull in competitors }
            val entries = (obj["entriesList"] as? JsonArray).orEmpty()
                .map { it.jsonObject }
                .filter {
                    it["competitorId"]?.jsonPrimitive?.contentOrNull in competitors &&
                        it["cancelled"]?.jsonPrimitive?.booleanOrNull != true
                }

            // Past = the competitor has a result; upcoming = entered but no result yet.
            val rows: List<Pair<String, JsonObject?>> = if (upcoming) {
                val resulted = results.mapNotNull { it["competitorId"]?.jsonPrimitive?.contentOrNull }.toSet()
                entries.mapNotNull { it["competitorId"]?.jsonPrimitive?.contentOrNull }
                    .filter { it !in resulted }.map { it to null }
            } else {
                results.mapNotNull { r -> r["competitorId"]?.jsonPrimitive?.contentOrNull?.let { it to r } }
            }

            for ((competitorId, r) in rows) {
                val info = competitors[competitorId]
                val cat = obj["category"]?.jsonObject
                val event = obj["event"]?.jsonObject
                result += Competition(
                    competitionId = obj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
                    competitionDate = date,
                    checkInEnd = obj["checkInEnd"]?.jsonPrimitive?.contentOrNull,
                    competitionType = obj["competitionType"]?.jsonPrimitive?.contentOrNull,
                    isFinal = r?.get("isFinal")?.jsonPrimitive?.booleanOrNull ?: false,
                    hasResult = r != null,
                    eventName = event?.get("name")?.jsonPrimitive?.contentOrNull,
                    eventLocation = event?.get("location")?.jsonPrimitive?.contentOrNull,
                    eventId = event?.get("id")?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
                    federation = obj["federation"]?.jsonPrimitive?.contentOrNull,
                    federatedPersonId = federatedPersonId,
                    personName = personName,
                    personId = personId,
                    competitorName = info?.name,
                    competitorId = competitorId,
                    competitorType = info?.type,
                    // Not exposed on Competition; only reachable via rounds, not worth the extra load.
                    dances = emptyList(),
                    participants = obj["participantsTotal"]?.jsonPrimitive?.intOrNull,
                    ranking = r?.get("ranking")?.jsonPrimitive?.intOrNull,
                    rankingTo = r?.get("rankingTo")?.jsonPrimitive?.intOrNull,
                    pointGain = r?.get("pointGain")?.jsonPrimitive?.contentOrNull,
                    category = cat?.let {
                        CompetitionCategory(
                            id = it["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
                            name = it["name"]?.jsonPrimitive?.contentOrNull,
                            series = it["series"]?.jsonPrimitive?.contentOrNull,
                            discipline = it["discipline"]?.jsonPrimitive?.contentOrNull,
                            ageGroup = it["ageGroup"]?.jsonPrimitive?.contentOrNull,
                            genderGroup = it["genderGroup"]?.jsonPrimitive?.contentOrNull,
                            competitorClass = it["class"]?.jsonPrimitive?.contentOrNull,
                            competitorType = it["competitorType"]?.jsonPrimitive?.contentOrNull,
                            baseDanceProgramId = it["baseDanceProgramId"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                        )
                    }
                )
            }
        }
        return result
    }

    private companion object {
        const val PAGE_SIZE = 50
        const val UPCOMING_PAGE_SIZE = 15
        const val MAX_PAGES = 20
        const val PERSON_CONCURRENCY = 4
    }
}
