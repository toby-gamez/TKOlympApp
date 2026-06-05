package com.tkolymp.tkolympapp

import com.tkolymp.shared.auth.AuthService
import com.tkolymp.tkolympapp.fakes.FakeGraphQlClient
import com.tkolymp.tkolympapp.fakes.FakeTokenStorage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthServiceTest {

    @Test
    fun `login stores token and returns true`() = runTest {
        val storage = FakeTokenStorage()
        val response = buildJsonObject {
            put("data", buildJsonObject {
                put("login", buildJsonObject {
                    put("result", buildJsonObject {
                        put("jwt", JsonPrimitive("header.payload.sig"))
                    })
                })
            })
        }
        val service = AuthService(storage, FakeGraphQlClient(response))
        assertTrue(service.login("user", "pass"))
        assertNotNull(storage.getToken())
    }

    @Test
    fun `login returns false when jwt absent`() = runTest {
        val storage = FakeTokenStorage()
        val response = buildJsonObject {
            put("data", buildJsonObject { put("login", buildJsonObject { put("result", buildJsonObject {}) }) })
        }
        val service = AuthService(storage, FakeGraphQlClient(response))
        assertFalse(service.login("user", "wrong"))
        assertNull(storage.getToken())
    }

    @Test
    fun `login returns false when network throws`() = runTest {
        val storage = FakeTokenStorage()
        val throwingClient = object : com.tkolymp.shared.network.IGraphQlClient {
            override suspend fun post(query: String, variables: JsonObject?) =
                throw RuntimeException("No network")
        }
        val service = AuthService(storage, throwingClient)
        assertFalse(service.login("user", "pass"))
    }

    @Test
    fun `hasToken returns false when no token`() = runTest {
        val service = AuthService(FakeTokenStorage(), FakeGraphQlClient())
        assertFalse(service.hasToken())
    }

    @Test
    fun `hasToken returns true for valid non-expired token`() = runTest {
        val exp2099 = 4102444800L
        val payload = """{"exp":$exp2099}"""
        @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
        val b64 = kotlin.io.encoding.Base64.UrlSafe.encode(payload.encodeToByteArray()).trimEnd('=')
        val storage = FakeTokenStorage("header.$b64.sig")
        val service = AuthService(storage, FakeGraphQlClient())
        assertTrue(service.hasToken())
    }
}
