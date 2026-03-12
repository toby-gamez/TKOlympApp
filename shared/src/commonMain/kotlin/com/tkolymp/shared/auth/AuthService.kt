package com.tkolymp.shared.auth

import com.tkolymp.shared.Logger
import com.tkolymp.shared.network.IGraphQlClient
import com.tkolymp.shared.storage.TokenStorage
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*

class AuthService(private val storage: TokenStorage, private val client: IGraphQlClient) : IAuthService {
    private val json = Json { ignoreUnknownKeys = true }
    private var currentToken: String? = null

    override suspend fun initialize() {
        currentToken = storage.getToken()
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
            return false
        }

        val token = resp.jsonObject["data"]
            ?.jsonObject?.get("login")
            ?.jsonObject?.get("result")
            ?.jsonObject?.get("jwt")
            ?.jsonPrimitive?.contentOrNull

        if (!token.isNullOrBlank()) {
            storage.saveToken(token)
            currentToken = token
            return true
        }

        val errors = resp.jsonObject["errors"]?.toString() ?: resp.toString()
        Logger.d("AuthService", "Login failed: $errors")

        return false
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
            currentToken = token
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
            exp < Clock.System.now().epochSeconds
        } catch (_: Exception) { true }
    }

    override suspend fun hasToken(): Boolean {
        val t = currentToken ?: return false
        if (isTokenExpired(t)) {
            return refreshJwt()
        }
        return true
    }

    override fun getToken(): String? = currentToken
}
