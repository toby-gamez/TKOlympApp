package com.tkolymp.tkolympapp

import com.tkolymp.shared.graphql.GraphQlOperations
import com.tkolymp.shared.network.GraphQlClientImpl
import com.tkolymp.shared.network.GraphQlException
import com.tkolymp.shared.network.GraphQlHttpException
import com.tkolymp.shared.network.IGraphQlClient
import com.tkolymp.shared.network.postAllNodes
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class GraphQlClientTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun client(
        token: () -> String? = { "t1" },
        onUnauthorized: (suspend () -> Boolean)? = null,
        handler: MockRequestHandler,
    ) = GraphQlClientImpl(HttpClient(MockEngine(handler)), "https://x/graphql", "1", { token() }, onUnauthorized)

    @Test
    fun `returns payload on success`() = runTest {
        val c = client { respond("""{"data":{"a":1}}""", HttpStatusCode.OK, json) }
        assertEquals(1, c.post("query { a }").jsonObject["data"]!!.jsonObject["a"]!!.jsonPrimitive.int)
    }

    @Test
    fun `html error page surfaces as http exception not a parse crash`() = runTest {
        val c = client { respond("<html>Bad gateway</html>", HttpStatusCode.BadGateway, headersOf(HttpHeaders.ContentType, "text/html")) }
        val ex = assertFailsWith<GraphQlHttpException> { c.post("query { a }") }
        assertEquals(502, ex.status)
    }

    @Test
    fun `graphql errors throw and keep partial data`() = runTest {
        val c = client { respond("""{"data":{"a":1,"b":null},"errors":[{"message":"boom"}]}""", HttpStatusCode.OK, json) }
        val ex = assertFailsWith<GraphQlException> { c.post("query { a b }") }
        assertEquals("boom", ex.message)
        assertEquals(1, ex.data!!.jsonObject["a"]!!.jsonPrimitive.int)
    }

    @Test
    fun `expired token refreshes and retries once with the new token`() = runTest {
        var token = "old"
        val seen = mutableListOf<String?>()
        var refreshes = 0
        val c = client(token = { token }, onUnauthorized = { refreshes++; token = "new"; true }) { req ->
            seen += req.headers[HttpHeaders.Authorization]
            if (req.headers[HttpHeaders.Authorization] == "Bearer old") respond("""{"errors":[{"message":"jwt expired"}]}""", HttpStatusCode.OK, json)
            else respond("""{"data":{"ok":true}}""", HttpStatusCode.OK, json)
        }
        c.post("query { ok }")
        assertEquals(listOf<String?>("Bearer old", "Bearer new"), seen)
        assertEquals(1, refreshes)
    }

    @Test
    fun `failed refresh surfaces the original error without looping`() = runTest {
        var calls = 0
        val c = client(onUnauthorized = { false }) { calls++; respond("""{"errors":[{"message":"jwt expired"}]}""", HttpStatusCode.Unauthorized, json) }
        assertFailsWith<GraphQlException> { c.post("query { a }") }
        assertEquals(1, calls)
    }

    @Test
    fun `refresh and login operations never trigger a refresh`() = runTest {
        var refreshes = 0
        val c = client(onUnauthorized = { refreshes++; true }) { respond("""{"errors":[{"message":"jwt expired"}]}""", HttpStatusCode.OK, json) }
        assertFailsWith<GraphQlException> { c.post(GraphQlOperations.Refresh) }
        assertFailsWith<GraphQlException> { c.post(GraphQlOperations.Login) }
        assertEquals(0, refreshes)
    }

    @Test
    fun `no token means no refresh attempt`() = runTest {
        var refreshes = 0
        val c = client(token = { null }, onUnauthorized = { refreshes++; true }) { respond("""{"errors":[{"message":"jwt expired"}]}""", HttpStatusCode.OK, json) }
        assertFailsWith<GraphQlException> { c.post("query { a }") }
        assertEquals(0, refreshes)
    }

    // --- postAllNodes ---

    private class PagedFake(private val total: Int) : IGraphQlClient {
        val offsets = mutableListOf<Int>()
        override suspend fun post(query: String, variables: JsonObject?): JsonElement {
            val first = variables!!["first"]!!.jsonPrimitive.int
            val offset = variables["offset"]!!.jsonPrimitive.int
            offsets += offset
            val nodes = (offset until minOf(total, offset + first)).map { buildJsonObject { put("id", it) } }
            return buildJsonObject { put("data", buildJsonObject { put("things", buildJsonObject { put("nodes", JsonArray(nodes)) }) }) }
        }
    }

    @Test
    fun `postAllNodes pages until a short page`() = runTest {
        val fake = PagedFake(total = 450)
        val all = fake.postAllNodes("q", "things", pageSize = 200)
        assertEquals(450, all!!.size)
        assertEquals(listOf(0, 200, 400), fake.offsets)
    }

    @Test
    fun `postAllNodes stops after an exactly full last page followed by an empty one`() = runTest {
        val fake = PagedFake(total = 400)
        assertEquals(400, fake.postAllNodes("q", "things", pageSize = 200)!!.size)
        assertEquals(listOf(0, 200, 400), fake.offsets)
    }

    @Test
    fun `postAllNodes returns null for a malformed first page`() = runTest {
        val bad = object : IGraphQlClient { override suspend fun post(query: String, variables: JsonObject?): JsonElement = buildJsonObject { } }
        assertNull(bad.postAllNodes("q", "things"))
    }

    @Test
    fun `postAllNodes forwards base variables`() = runTest {
        var seen: JsonObject? = null
        val c = object : IGraphQlClient {
            override suspend fun post(query: String, variables: JsonObject?): JsonElement {
                seen = variables
                return buildJsonObject { put("data", buildJsonObject { put("things", buildJsonObject { put("nodes", JsonArray(emptyList())) }) }) }
            }
        }
        c.postAllNodes("q", "things", buildJsonObject { put("sticky", true) })
        assertEquals(true, seen!!["sticky"]!!.jsonPrimitive.boolean)
    }
}
