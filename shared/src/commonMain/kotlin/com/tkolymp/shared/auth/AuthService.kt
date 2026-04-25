package com.tkolymp.shared.auth

import com.tkolymp.shared.Logger
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.network.IGraphQlClient
import com.tkolymp.shared.storage.TokenStorage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

class AuthService(private val storage: TokenStorage, private val client: IGraphQlClient) : IAuthService {
    private val json = Json { ignoreUnknownKeys = true }

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

    override suspend fun refreshJwt(): Boolean {
        val query = "query Refresh { refreshJwt }"

        val resp = try {
            client.post(query, null)
        } catch (ex: Exception) {
            Logger.d("AuthService", "refreshJwt failed: ${ex.message}")
            return false
        }

        val token = resp.jsonObject["data"]
            ?.jsonObject?.get("refreshJwt")
            ?.jsonPrimitive?.contentOrNull

        if (!token.isNullOrBlank()) {
            storage.saveToken(token)
            return true
        }

        val errors = resp.jsonObject["errors"]?.toString() ?: resp.toString()
        Logger.d("AuthService", "refreshJwt failed: $errors")
        return false
    }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    private fun isTokenExpired(token: String): Boolean {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return true
            val payload = kotlin.io.encoding.Base64.UrlSafe.decode(parts[1])
            val jsonObj = json.parseToJsonElement(payload.decodeToString()).jsonObject
            val exp = jsonObj["exp"]?.jsonPrimitive?.long ?: return true
            exp < kotlin.time.Clock.System.now().epochSeconds
        } catch (_: Exception) { true }
    }

    override suspend fun hasToken(): Boolean {
        val t = storage.getToken() ?: return false
        if (isTokenExpired(t)) {
            // Try to refresh; if it fails (e.g. offline) keep the session alive so the
            // app can still work from cache. The server will reject API calls if the token
            // is truly invalid, but we must not force a login just because there is no
            // internet connection.
            val refreshed = refreshJwt()
            if (!refreshed) {
                val online = try { ServiceLocator.networkMonitor.isConnected() } catch (_: Exception) { true }
                if (online) {
                    // We are online but refresh failed repeatedly -> token likely invalid.
                    // Clear stored token so caller can force login flow.
                    try { storage.clear() } catch (_: Exception) {}
                    Logger.d("AuthService", "Token expired and refresh failed while online — clearing token")
                    return false
                } else {
                    Logger.d("AuthService", "Token expired but refresh failed — keeping session for offline use")
                }
            }
        }
        return true
    }

    override suspend fun getToken(): String? = storage.getToken()
}
