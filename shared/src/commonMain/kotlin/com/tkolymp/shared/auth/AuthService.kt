package com.tkolymp.shared.auth

import com.tkolymp.shared.Logger
import com.tkolymp.shared.network.GraphQlException
import com.tkolymp.shared.network.IGraphQlClient
import com.tkolymp.shared.storage.ITokenStorage
import com.tkolymp.shared.json.AppJson
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

class AuthService(private val storage: ITokenStorage, private val client: IGraphQlClient) : IAuthService {
    

    override suspend fun initialize() {
        // Token lives only in secure storage; no in-memory cache needed.
    }

    override suspend fun login(username: String, password: String): Boolean {
        val mutation = """
            mutation Login(${'$'}login: String!, ${'$'}passwd: String!) {
                login(input: {login: ${'$'}login, passwd: ${'$'}passwd}) { result { jwt } }
            }
        """.trimIndent()
        val variables = buildJsonObject {
            put("login", username)
            put("passwd", password)
        }

        val resp = try {
            client.post(mutation, variables)
        } catch (ex: Exception) {
            Logger.d("AuthService", "Login request failed: ${ex.message}")
            return false
        }

        return try {
            val token = resp.jsonObject["data"]
                ?.jsonObject?.get("login")
                ?.jsonObject?.get("result")
                ?.jsonObject?.get("jwt")
                ?.jsonPrimitive?.contentOrNull

            if (!token.isNullOrBlank()) {
                storage.saveToken(token)
                true
            } else {
                val errors = resp.jsonObject["errors"]?.toString() ?: resp.toString()
                Logger.d("AuthService", "Login failed: $errors")
                false
            }
        } catch (ex: Exception) {
            Logger.d("AuthService", "Login response parse failed: ${ex.message}")
            false
        }
    }

    /** Distinguishes a definitive server-side rejection from a transport failure of unknown cause. */
    private sealed interface RefreshOutcome {
        data object Success : RefreshOutcome
        data object Rejected : RefreshOutcome
        data object Unreachable : RefreshOutcome
    }

    private suspend fun attemptRefresh(): RefreshOutcome {
        val query = "query Refresh { refreshJwt }"

        val resp = try {
            client.post(query, null)
        } catch (ex: GraphQlException) {
            // The server was reached and explicitly returned a GraphQL error (e.g. invalid/expired refresh token).
            Logger.d("AuthService", "refreshJwt rejected: ${ex.message}")
            return RefreshOutcome.Rejected
        } catch (ex: Exception) {
            // Transport-level failure (timeout, DNS, TLS/cert-pin hiccup, 5xx, ...) — we never got a verdict
            // from the server, so we cannot conclude the token is actually invalid.
            Logger.d("AuthService", "refreshJwt unreachable: ${ex.message}")
            return RefreshOutcome.Unreachable
        }

        val token = resp.jsonObject["data"]
            ?.jsonObject?.get("refreshJwt")
            ?.jsonPrimitive?.contentOrNull

        if (!token.isNullOrBlank()) {
            storage.saveToken(token)
            return RefreshOutcome.Success
        }

        Logger.d("AuthService", "refreshJwt returned no token: $resp")
        return RefreshOutcome.Rejected
    }

    override suspend fun refreshJwt(): Boolean = attemptRefresh() == RefreshOutcome.Success

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    private fun isTokenExpired(token: String): Boolean {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return true
            val payload = kotlin.io.encoding.Base64.UrlSafe.decode(parts[1])
            val jsonObj = AppJson.parseToJsonElement(payload.decodeToString()).jsonObject
            val exp = jsonObj["exp"]?.jsonPrimitive?.long ?: return true
            exp < kotlin.time.Clock.System.now().epochSeconds
        } catch (_: Exception) { true }
    }

    override suspend fun hasToken(): Boolean {
        val t = storage.getToken() ?: return false
        if (isTokenExpired(t)) {
            // Only clear the stored token when the server actually had a chance to weigh in and
            // explicitly rejected the refresh. A device-level "online" check (NetworkMonitor) can't
            // tell us the API itself was reachable, so a transient timeout/DNS/cert hiccup here must
            // not be treated the same as a real rejection — otherwise a flaky request (easily hit by
            // the widget's periodic background refresh) silently logs the user out.
            when (attemptRefresh()) {
                RefreshOutcome.Success -> {}
                RefreshOutcome.Rejected -> {
                    try { storage.clear() } catch (_: Exception) {}
                    Logger.d("AuthService", "Refresh token rejected by server — clearing token")
                    return false
                }
                RefreshOutcome.Unreachable -> {
                    Logger.d("AuthService", "Token expired but server unreachable — keeping session")
                }
            }
        }
        return true
    }

    override suspend fun getToken(): String? = storage.getToken()
}
