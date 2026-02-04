package com.tkolymp.shared.auth

import com.tkolymp.shared.network.IGraphQlClient
import com.tkolymp.shared.storage.TokenStorage
import kotlinx.serialization.json.*

class AuthService(private val storage: TokenStorage, private val client: IGraphQlClient) : IAuthService {
    private val json = Json { ignoreUnknownKeys = true }
    private var currentToken: String? = null

    override suspend fun initialize() {
        currentToken = storage.getToken()
    }

    override suspend fun login(username: String, password: String): Boolean {
        val escUsername = username.replace("\\", "\\\\").replace("\"", "\\\"")
        val escPassword = password.replace("\\", "\\\\").replace("\"", "\\\"")
        val mutation = "mutation { login(input: {login: \"$escUsername\", passwd: \"$escPassword\"}) { result { jwt } } }"

        val resp = try {
            client.post(mutation, null)
        } catch (ex: Exception) {
            return false
        }

        val token = resp.jsonObject["data"]
            ?.jsonObject?.get("login")
            ?.jsonObject?.get("result")
            ?.jsonObject?.get("jwt")
            ?.jsonPrimitive?.contentOrNull

        if (token != null) {
            storage.saveToken(token)
            currentToken = token
            return true
        }

        return false
    }

    override suspend fun refreshJwt(): Boolean {
        // Implement your refresh mutation/call here when backend supports it.
        return false
    }

    override suspend fun hasToken(): Boolean = currentToken != null

    override fun getToken(): String? = currentToken
}
