package com.tkolymp.shared.competitivedata

import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.Logger
import com.tkolymp.shared.graphql.GraphQlOperations
import com.tkolymp.shared.json.AppJson
import com.tkolymp.shared.network.IGraphQlClient
import com.tkolymp.shared.network.postAllNodes
import com.tkolymp.shared.storage.OfflineDataStorage
import com.tkolymp.shared.sync.OfflineKeys
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.minutes

/** Reads the public "Soutěžní data" (federation events and competitions of CSTS, WDSF, SZTS). */
class CompetitiveDataService(
    private val client: IGraphQlClient = ServiceLocator.graphQlClient,
    private val cache: CacheService = ServiceLocator.cacheService,
    private val offline: OfflineDataStorage? = null
) : ICompetitiveDataService {

    override suspend fun getFederations(): List<DanceFederation> {
        cache.get<List<DanceFederation>>("cdata_federations")?.let { return it }
        val loaded = withOffline(OfflineKeys.CDATA_FEDERATIONS) {
            val resp = client.post(GraphQlOperations.Federations, JsonObject(emptyMap()))
            (resp.data()?.get("federationsList") as? JsonArray).orEmpty().mapNotNull {
                val o = it.jsonObject
                DanceFederation(o.str("code") ?: return@mapNotNull null, o.str("name") ?: return@mapNotNull null)
            }
        }
        if (loaded.data.isNotEmpty() && !loaded.fromOffline) cache.put("cdata_federations", loaded.data, ttl = 60.minutes)
        return loaded.data
    }

    override suspend fun getYears(federation: String): List<FederationYear> {
        val key = "cdata_years_$federation"
        cache.get<List<FederationYear>>(key)?.let { return it }
        val loaded = withOffline(OfflineKeys.cdataYears(federation)) {
            val resp = client.post(GraphQlOperations.FederationEventYears, buildJsonObject { put("code", federation) })
            (resp.data()?.get("federation")?.asObj()?.get("eventFacets")?.asObj()?.get("years") as? JsonArray)
                .orEmpty().mapNotNull {
                    val o = it as? JsonObject ?: return@mapNotNull null
                    FederationYear(o.int("year") ?: return@mapNotNull null, o.int("eventCount") ?: 0, o.int("mappedEventCount") ?: 0)
                }.sortedBy { it.year }
        }
        if (!loaded.fromOffline) cache.put(key, loaded.data, ttl = 15.minutes)
        return loaded.data
    }

    override suspend fun getEvents(federation: String, year: Int, forceRefresh: Boolean): LoadedData<List<FederationEventSummary>> {
        val key = "cdata_events_${federation}_$year"
        if (forceRefresh) cache.invalidate(key) else cache.get<List<FederationEventSummary>>(key)?.let { return LoadedData(it) }
        val loaded = withOffline(OfflineKeys.cdataEvents(federation, year)) {
            val vars = buildJsonObject {
                put("federation", federation)
                put("since", "$year-01-01")
                put("until", "${year + 1}-01-01")
            }
            val nodes = client.postAllNodes(GraphQlOperations.FederationEvents, "federationEvents", vars) ?: error("Malformed response")
            nodes.mapNotNull { parseSummary(it.jsonObject) }.sortedWith(compareBy({ it.startDate }, { it.name }))
        }
        // An empty "success" is a legitimate answer here (e.g. SZTS has no data yet) but is cached only briefly.
        if (!loaded.fromOffline) cache.put(key, loaded.data, ttl = if (loaded.data.isEmpty()) 1.minutes else 10.minutes)
        return loaded
    }

    override suspend fun getEventDetail(eventId: Long): FederationEventDetail? {
        val key = "cdata_event_$eventId"
        cache.get<FederationEventDetail>(key)?.let { return it }
        val resp = client.post(GraphQlOperations.FederationEventDetail, buildJsonObject { put("id", eventId.toString()) })
        val e = resp.data()?.get("federationEvent")?.asObj() ?: return null
        val comps = (e["competitions"]?.asObj()?.get("nodes") as? JsonArray).orEmpty().mapNotNull { n ->
            val o = n.jsonObject
            val cat = o["category"]?.asObj()
            FederationCompetitionSummary(
                id = o.str("id")?.toLongOrNull() ?: return@mapNotNull null,
                startDate = o.str("startDate") ?: return@mapNotNull null,
                competitionType = o.str("competitionType"),
                status = o.str("status") ?: "UNKNOWN",
                participants = o.int("participantsTotal"),
                categoryName = cat?.str("name"),
                discipline = cat?.str("discipline"),
                ageGroup = cat?.str("ageGroup"),
                competitorClass = cat?.str("class")
            )
        }
        val detail = FederationEventDetail(
            id = eventId,
            federation = e.str("federation") ?: "",
            name = e.str("name"), startDate = e.str("startDate") ?: return null, endDate = e.str("endDate"),
            city = e.str("city"), location = e.str("location"), country = e.str("country"),
            streetAddress = e.str("streetAddress"), postalCode = e.str("postalCode"), addressNote = e.str("addressNote"),
            contactName = e.str("contactName"), contactPhone = e.str("contactPhone"), contactEmail = e.str("contactEmail"),
            websiteUrl = e.str("websiteUrl"),
            lat = e.dbl("venueLat"), lng = e.dbl("venueLng"),
            status = e.str("status") ?: "UNKNOWN",
            organizer = e["organizingClub"]?.asObj()?.str("name"),
            competitions = comps
        )
        cache.put(key, detail, ttl = 10.minutes)
        return detail
    }

    override suspend fun getCompetitionResults(competitionId: Long): FederationCompetitionResults? {
        val key = "cdata_results_$competitionId"
        cache.get<FederationCompetitionResults>(key)?.let { return it }
        val resp = client.post(GraphQlOperations.FederationCompetitionResults, buildJsonObject { put("id", competitionId.toString()) })
        val c = resp.data()?.get("competition")?.asObj() ?: return null
        val rows = (c["resultsList"] as? JsonArray).orEmpty().mapNotNull { n ->
            val o = n.jsonObject
            val competitor = o["competitor"]?.asObj()
            val name = competitor?.str("name")?.takeIf { it.isNotBlank() }
                ?: (competitor?.get("peopleList") as? JsonArray)?.mapNotNull { it.asObj()?.str("name") }
                    ?.joinToString(" - ")?.takeIf { it.isNotBlank() }
            FederationResultRow(
                o.int("ranking") ?: return@mapNotNull null, o.int("rankingTo"), o.str("pointGain"), name,
                isFinal = (o["isFinal"] as? JsonPrimitive)?.booleanOrNull ?: false
            )
        }
        val result = FederationCompetitionResults(
            id = competitionId,
            startDate = c.str("startDate") ?: "",
            competitionType = c.str("competitionType"),
            status = c.str("status") ?: "UNKNOWN",
            participants = c.int("participantsTotal"),
            categoryName = c["category"]?.asObj()?.str("name"),
            results = rows
        )
        cache.put(key, result, ttl = 10.minutes)
        return result
    }

    private fun parseSummary(o: JsonObject): FederationEventSummary? = FederationEventSummary(
        id = o.str("id")?.toLongOrNull() ?: return null,
        name = o.str("name"),
        startDate = o.str("startDate") ?: return null,
        endDate = o.str("endDate"),
        city = o.str("city"), location = o.str("location"), country = o.str("country"),
        lat = o.dbl("venueLat"), lng = o.dbl("venueLng"),
        status = o.str("status") ?: "UNKNOWN",
        competitionCount = o["competitions"]?.asObj()?.int("totalCount") ?: 0,
        types = COMPETITION_TYPES.filter { (o["type$it"]?.asObj()?.int("totalCount") ?: 0) > 0 }.toSet(),
        hasResults = (o["completedCompetitions"]?.asObj()?.int("totalCount") ?: 0) > 0
    )

    /**
     * Runs [fetch]; on success saves the result for offline use, on failure serves the saved copy (or rethrows when
     * there is none). An empty result never overwrites a non-empty saved copy: the server has been seen to answer
     * "success" with nothing under load.
     */
    private suspend inline fun <reified T : Collection<*>> withOffline(key: String, fetch: () -> T): LoadedData<T> {
        val store = offline
        try {
            val fresh = fetch()
            if (store != null) try {
                if (fresh.isNotEmpty() || load<T>(store, key).isNullOrEmpty()) store.save(key, AppJson.encodeToString(fresh))
            } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("CompetitiveDataService", "offline save $key failed: ${e.message}") }
            return LoadedData(fresh)
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            val saved = store?.let { load<T>(it, key) } ?: throw e
            Logger.d("CompetitiveDataService", "serving saved $key: ${e.message}")
            return LoadedData(saved, fromOffline = true)
        }
    }

    private suspend inline fun <reified T> load(store: OfflineDataStorage, key: String): T? =
        try { store.load(key)?.let { AppJson.decodeFromString<T>(it) } } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    private companion object {
        val COMPETITION_TYPES = listOf("CUP", "RANKING", "LEAGUE", "CHAMPIONSHIP", "TOP_LEVEL", "SUPER_LEAGUE", "G_CUP")
    }

    private fun JsonElement.asObj(): JsonObject? = this as? JsonObject
    private fun JsonElement.data(): JsonObject? = (this as? JsonObject)?.get("data") as? JsonObject
    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    private fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.dbl(k: String): Double? = (this[k] as? JsonPrimitive)?.doubleOrNull
}
