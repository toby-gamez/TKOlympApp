package com.tkolymp.tkolympapp

import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.competitions.CompetitionService
import com.tkolymp.shared.network.IGraphQlClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class CompetitionServiceTest {

    private class ScriptedClient(private val respond: suspend (Int) -> JsonElement) : IGraphQlClient {
        var calls = 0
        override suspend fun post(query: String, variables: JsonObject?): JsonElement = respond(++calls)
    }

    private fun competitions(entries: String) = Json.parseToJsonElement(
        """{"data":{"competitions":{"nodes":[{
            "id":"10","federation":"csts","startDate":"2026-11-21","checkInEnd":"09:00:00",
            "competitionType":"COMPETITION","participantsTotal":30,
            "category":{"id":"5","name":"Adult Latin"},"event":{"id":"7","name":"Cup","location":"Brno"},
            "officials":{"nodes":[]},"entriesList":$entries,"resultsList":[]
        }]}}}"""
    )

    private val mineAndOther = """[
        {"competitorId":"c1","cancelled":false,"competitor":{"id":"c1","name":"","competitorType":"COUPLE",
            "peopleList":[{"id":"1","name":"Ann"},{"id":"2","name":"Bob"}]}},
        {"competitorId":"c9","cancelled":false,"competitor":{"id":"c9","name":"Other","competitorType":"SOLO",
            "peopleList":[{"id":"99","name":"Stranger"}]}}
    ]"""

    @Test
    fun `keeps only requested people and names couples by partners`() = runTest {
        val service = CompetitionService(ScriptedClient { competitions(mineAndOther) }, CacheService())

        val list = service.getUpcomingCompetitions("2026-11-16", "2026-11-23", pPersonIds = listOf(1L))

        assertEquals(1, list.size)
        assertEquals("Ann - Bob", list.single().competitorName)
        assertEquals(1L, list.single().personId)
        assertEquals("Brno", list.single().eventLocation)
    }

    @Test
    fun `failed fetch is not cached`() = runTest {
        val client = ScriptedClient { call -> if (call <= 2) error("timeout") else competitions(mineAndOther) }
        val service = CompetitionService(client, CacheService())

        val failed = service.getUpcomingCompetitions("2026-11-16", "2026-11-23", pPersonIds = listOf(1L))
        val retried = service.getUpcomingCompetitions("2026-11-16", "2026-11-23", pPersonIds = listOf(1L))

        assertEquals(0, failed.size)
        assertEquals(1, retried.size)
    }

    @Test
    fun `successful fetch is cached`() = runTest {
        val client = ScriptedClient { competitions(mineAndOther) }
        val service = CompetitionService(client, CacheService())

        service.getUpcomingCompetitions("2026-11-16", "2026-11-23", pPersonIds = listOf(1L))
        service.getUpcomingCompetitions("2026-11-16", "2026-11-23", pPersonIds = listOf(1L))

        assertEquals(1, client.calls)
    }

    @Test
    fun `cancelling the caller does not discard the fetch`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val client = ScriptedClient { gate.await(); competitions(mineAndOther) }
        val service = CompetitionService(client, CacheService())

        val caller = launch(Dispatchers.Default) {
            service.getUpcomingCompetitions("2026-11-16", "2026-11-23", pPersonIds = listOf(1L))
        }
        while (client.calls == 0) withContext(Dispatchers.Default) { kotlinx.coroutines.yield() }
        caller.cancel()
        gate.complete(Unit)
        val list = service.getUpcomingCompetitions("2026-11-16", "2026-11-23", pPersonIds = listOf(1L))

        assertEquals(1, list.size)
        assertEquals(1, client.calls)
    }
}
