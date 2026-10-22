package com.tkolymp.tkolympapp

import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.competitivedata.CompetitiveDataService
import com.tkolymp.shared.network.IGraphQlClient
import com.tkolymp.tkolympapp.fakes.FakeOfflineDataStorage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class SwitchableClient(var online: Boolean, var events: List<String>) : IGraphQlClient {
    override suspend fun post(query: String, variables: JsonObject?): JsonElement {
        if (!online) error("no network")
        val nodes = JsonArray(events.mapIndexed { i, name ->
            buildJsonObject { put("id", "${i + 1}"); put("name", name); put("startDate", "2026-05-0${i + 1}"); put("status", "PLANNED") }
        })
        return buildJsonObject { put("data", buildJsonObject { put("federationEvents", buildJsonObject { put("nodes", nodes) }) }) }
    }
}

class CompetitiveDataServiceOfflineTest {
    private fun service(client: IGraphQlClient, storage: FakeOfflineDataStorage) =
        // A fresh CacheService per call so only the offline copy can answer.
        CompetitiveDataService(client, CacheService(), storage)

    @Test fun servesSavedEventsWhenTheRequestFails() = runTest {
        val storage = FakeOfflineDataStorage()
        val client = SwitchableClient(online = true, events = listOf("Jarní pohár", "Letní cena"))
        val live = service(client, storage).getEvents("csts", 2026)
        assertFalse(live.fromOffline)

        client.online = false
        val saved = service(client, storage).getEvents("csts", 2026)
        assertTrue(saved.fromOffline)
        assertEquals(listOf("Jarní pohár", "Letní cena"), saved.data.map { it.name })
    }

    @Test fun failsWhenNothingWasSaved() = runTest {
        val client = SwitchableClient(online = false, events = emptyList())
        assertFailsWith<IllegalStateException> { service(client, FakeOfflineDataStorage()).getEvents("csts", 2026) }
    }

    @Test fun emptyResponseDoesNotOverwriteSavedEvents() = runTest {
        val storage = FakeOfflineDataStorage()
        val client = SwitchableClient(online = true, events = listOf("Jarní pohár"))
        service(client, storage).getEvents("csts", 2026)

        client.events = emptyList() // server "succeeds" with nothing
        assertTrue(service(client, storage).getEvents("csts", 2026).data.isEmpty())

        client.online = false
        assertEquals(listOf("Jarní pohár"), service(client, storage).getEvents("csts", 2026).data.map { it.name })
    }
}
