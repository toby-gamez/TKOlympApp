package com.tkolymp.shared.competitions

import com.tkolymp.shared.graphql.GraphQlOperations
import com.tkolymp.shared.network.postAllNodes
import com.tkolymp.shared.Logger
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.network.IGraphQlClient
import com.tkolymp.shared.sync.OfflineSyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

// Competitions are read with one date-filtered `competitions` query per chunk of club people:
// `condition.date` limits the rows to the requested range and `condition.competitor.personIds`
// (also applied to `entriesList`/`resultsList`) limits them to the club's competitors, which
// carry their local people inline. There is no club-wide filter, so "all" (pPersonIds == null)
// is resolved to the club's people first (cached for an hour).
//
// Fetches run in their own scope and are shared per cache key: a caller that goes away (the
// calendar cancels on every week/tab change) does not throw away work in progress, and the
// finished result lands in the cache for the next request.

class CompetitionService(
    private val client: IGraphQlClient = ServiceLocator.graphQlClient,
    private val cache: CacheService = ServiceLocator.cacheService
) : ICompetitionService {
    private val offlineSyncManager: OfflineSyncManager?
        get() = try { ServiceLocator.offlineSyncManager } catch (_: Exception) { null }

    private val rangeQuery = GraphQlOperations.CompetitionsInRange
    private val clubPeopleQuery = GraphQlOperations.CompetitionClubPeople

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val inFlight = mutableMapOf<String, Deferred<List<Competition>>>()
    private val inFlightLock = Mutex()

    private class Fetched(val list: List<Competition>, val complete: Boolean)

    // Returns the cached list, or joins/starts the fetch for this key. Only complete results are
    // cached: a failed or partial one must not be served again on refresh.
    private suspend fun cachedOrFetch(cacheKey: String, ttl: Duration, load: suspend () -> Fetched): List<Competition> {
        cache.get<List<Competition>>(cacheKey)?.let { return it }
        val job = inFlightLock.withLock {
            inFlight.getOrPut(cacheKey) {
                scope.async {
                    try {
                        val fetched = load()
                        if (fetched.complete) {
                            try { cache.put(cacheKey, fetched.list, ttl = ttl) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
                        }
                        fetched.list
                    } finally {
                        inFlightLock.withLock { inFlight.remove(cacheKey) }
                    }
                }
            }
        }
        return job.await()
    }

    override suspend fun getStoredUpcoming(pSince: String, pUntil: String, pPersonIds: List<Long>?): List<Competition> =
        try {
            offlineSyncManager?.loadCompetitions().orEmpty().filter { c ->
                c.competitionDate >= pSince && c.competitionDate <= pUntil &&
                    (pPersonIds == null || c.personId in pPersonIds)
            }.sortedBy { it.competitionDate }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }

    override suspend fun getUpcomingCompetitions(
        pSince: String?,
        pUntil: String?,
        first: Int,
        pPersonIds: List<Long>?
    ): List<Competition> {
        val personKey = pPersonIds?.joinToString(",") ?: "all"
        val cacheKey = "competitions_upcoming_${pSince}_${pUntil}_${first}_$personKey"
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val since = pSince ?: today.toString()
        val until = pUntil ?: today.plus(1, DateTimeUnit.YEAR).toString()
        return cachedOrFetch(cacheKey, 5.minutes) {
            try {
                val fetched = fetch(pPersonIds, since, until, upcoming = true)
                check(fetched.complete || fetched.list.isNotEmpty()) { "no competitions could be fetched" }
                Fetched(
                    fetched.list.distinctBy { dedupeKey(it) }.sortedBy { it.competitionDate }.take(first),
                    fetched.complete
                )
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Logger.d("CompetitionService", "upcoming fetch failed: ${e.message}")
                val offline = offlineSyncManager?.loadCompetitions()?.filter { c ->
                    c.competitionDate >= since && c.competitionDate <= until &&
                        (pPersonIds == null || c.personId in pPersonIds)
                }?.sortedBy { it.competitionDate }?.take(first) ?: emptyList()
                Fetched(offline, complete = false)
            }
        }
    }

    override suspend fun getPastCompetitions(
        pSince: String?,
        pUntil: String?,
        first: Int,
        pPersonIds: List<Long>?
    ): List<Competition> {
        val personKey = pPersonIds?.joinToString(",") ?: "all"
        val cacheKey = "competitions_past_${pSince}_${pUntil}_${first}_$personKey"
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val since = pSince ?: today.minus(1, DateTimeUnit.YEAR).toString()
        val until = pUntil ?: today.toString()
        return cachedOrFetch(cacheKey, 10.minutes) {
            try {
                val fetched = fetch(pPersonIds, since, until, upcoming = false)
                check(fetched.complete || fetched.list.isNotEmpty()) { "no competitions could be fetched" }
                Fetched(
                    fetched.list.distinctBy { dedupeKey(it) }.sortedByDescending { it.competitionDate }.take(first),
                    fetched.complete
                )
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Logger.d("CompetitionService", "past fetch failed: ${e.message}")
                val offline = offlineSyncManager?.loadPastCompetitions()?.filter { c ->
                    c.competitionDate >= since && c.competitionDate <= until &&
                        (pPersonIds == null || c.personId in pPersonIds)
                }?.sortedByDescending { it.competitionDate }?.take(first) ?: emptyList()
                Fetched(offline, complete = false)
            }
        }
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

    // One slow or failing chunk must not drop the rest, so each chunk is retried once and then
    // skipped; `complete` tells callers not to cache the result.
    private suspend fun fetch(
        pPersonIds: List<Long>?,
        since: String,
        until: String,
        upcoming: Boolean
    ): Fetched {
        val ids = pPersonIds ?: clubPersonIds() ?: return Fetched(emptyList(), complete = false)
        if (ids.isEmpty()) return Fetched(emptyList(), complete = true)
        // The server's `until` is exclusive, the callers' is inclusive.
        val untilExclusive = LocalDate.parse(until).plus(1, DateTimeUnit.DAY).toString()
        val gate = Semaphore(CHUNK_CONCURRENCY)
        val parts = coroutineScope {
            ids.chunked(CHUNK_SIZE).map { chunk ->
                async { gate.withPermit { fetchChunkOrNull(chunk, since, until, untilExclusive, upcoming) } }
            }.awaitAll()
        }
        return Fetched(parts.filterNotNull().flatten(), complete = parts.all { it != null })
    }

    private suspend fun fetchChunkOrNull(ids: List<Long>, since: String, until: String, untilExclusive: String, upcoming: Boolean): List<Competition>? {
        repeat(CHUNK_ATTEMPTS) { attempt ->
            try {
                return fetchChunk(ids, since, until, untilExclusive, upcoming)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Logger.d("CompetitionService", "chunk of ${ids.size} attempt ${attempt + 1} failed: ${e.message}")
            }
        }
        return null
    }

    private suspend fun fetchChunk(ids: List<Long>, since: String, until: String, untilExclusive: String, upcoming: Boolean): List<Competition> {
        val idSet = ids.toSet()
        val out = mutableListOf<Competition>()
        var offset = 0
        repeat(MAX_PAGES) {
            val vars = buildJsonObject {
                put("since", JsonPrimitive(since))
                put("until", JsonPrimitive(untilExclusive))
                put("personIds", JsonArray(ids.map { JsonPrimitive(it.toString()) }))
                put("first", JsonPrimitive(PAGE_SIZE))
                put("offset", JsonPrimitive(offset))
            }
            val resp = client.post(rangeQuery, vars)
            val nodes = (resp.jsonObject["data"]?.jsonObject?.get("competitions")?.jsonObject?.get("nodes") as? JsonArray)
                ?: error("competitions response has no nodes")
            out += parseCompetitions(nodes, idSet, since, until, upcoming)
            if (nodes.size < PAGE_SIZE) return out
            offset += PAGE_SIZE
        }
        return out
    }

    // Only people with at least one federation competitor can have competitions. Cached for an
    // hour: the club's roster hardly changes. Null when the people query itself failed (as
    // opposed to the club having none).
    private suspend fun clubPersonIds(): List<Long>? {
        cache.get<List<Long>>(CLUB_IDS_KEY)?.let { return it }
        val nodes = client.postAllNodes(clubPeopleQuery, "people") ?: return null
        val ids = nodes.mapNotNull { n ->
            val obj = n.jsonObject
            val hasCompetitor = (obj["federationPeople"]?.jsonObject?.get("nodes") as? JsonArray)?.any { fp ->
                (fp.jsonObject["competitorComponents"]?.jsonObject?.get("totalCount")?.jsonPrimitive?.intOrNull ?: 0) > 0
            } ?: false
            if (hasCompetitor) obj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() else null
        }
        try { cache.put(CLUB_IDS_KEY, ids, ttl = 1.hours) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        return ids
    }

    private class CompetitorInfo(val name: String?, val type: String?, val personId: Long?, val personName: String?)

    // Null when the competitor has none of the requested people, in case the server ignored the
    // nested personIds filter. A competitor without a people list is kept.
    private fun competitorInfo(c: JsonObject, ids: Set<Long>): CompetitorInfo? {
        val people = (c["peopleList"] as? JsonArray).orEmpty().map { it.jsonObject }
        fun JsonObject.isRequested() = this["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()?.let { it in ids } == true
        if (people.isNotEmpty() && people.none { it.isRequested() }) return null
        val own = people.firstOrNull { it.isRequested() } ?: people.firstOrNull()
        // Couples have an empty name server-side; show the partners instead.
        val name = c["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: people.mapNotNull { it["name"]?.jsonPrimitive?.contentOrNull }
                .joinToString(" - ").takeIf { it.isNotBlank() }
        return CompetitorInfo(
            name = name,
            type = c["competitorType"]?.jsonPrimitive?.contentOrNull,
            personId = own?.get("id")?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
            personName = own?.get("name")?.jsonPrimitive?.contentOrNull
        )
    }

    private fun parseCompetitions(
        competitions: List<JsonElement>,
        ids: Set<Long>,
        since: String,
        until: String,
        upcoming: Boolean
    ): List<Competition> {
        val result = mutableListOf<Competition>()
        for (elem in competitions) {
            val obj = elem.jsonObject
            val date = obj["startDate"]?.jsonPrimitive?.contentOrNull ?: continue
            if (date < since || date > until) continue

            val infos = mutableMapOf<String, CompetitorInfo>()
            fun rowsOf(key: String, skip: (JsonObject) -> Boolean = { false }): List<JsonObject> =
                (obj[key] as? JsonArray).orEmpty().map { it.jsonObject }.filter { row ->
                    if (skip(row)) return@filter false
                    val competitorId = row["competitorId"]?.jsonPrimitive?.contentOrNull ?: return@filter false
                    val info = (row["competitor"] as? JsonObject)?.let { competitorInfo(it, ids) }
                        ?: infos[competitorId]
                    if (info != null) infos[competitorId] = info
                    info != null
                }
            val results = rowsOf("resultsList")
            val entries = rowsOf("entriesList") { it["cancelled"]?.jsonPrimitive?.booleanOrNull == true }

            val officials = ((obj["officials"]?.jsonObject?.get("nodes") as? JsonArray).orEmpty())
                .map { it.jsonObject }
                .map { o ->
                    CompetitionOfficial(
                        name = o["person"]?.jsonObject?.get("canonicalName")?.jsonPrimitive?.contentOrNull,
                        role = o["role"]?.jsonPrimitive?.contentOrNull,
                        judgeLabel = o["judgeLabel"]?.jsonPrimitive?.contentOrNull
                    )
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
                val info = infos[competitorId]
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
                    federatedPersonId = null,
                    personName = info?.personName,
                    personId = info?.personId,
                    competitorName = info?.name,
                    competitorId = competitorId,
                    competitorType = info?.type,
                    // Not exposed on Competition; only reachable via rounds, not worth the extra load.
                    dances = emptyList(),
                    participants = obj["participantsTotal"]?.jsonPrimitive?.intOrNull,
                    ranking = r?.get("ranking")?.jsonPrimitive?.intOrNull,
                    rankingTo = r?.get("rankingTo")?.jsonPrimitive?.intOrNull,
                    pointGain = r?.get("pointGain")?.jsonPrimitive?.contentOrNull,
                    officials = officials,
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
        const val MAX_PAGES = 10
        const val CHUNK_SIZE = 25
        const val CHUNK_CONCURRENCY = 3
        const val CHUNK_ATTEMPTS = 2
        const val CLUB_IDS_KEY = "competitions_club_person_ids"
    }
}
