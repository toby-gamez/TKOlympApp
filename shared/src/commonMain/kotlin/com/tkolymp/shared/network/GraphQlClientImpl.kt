package com.tkolymp.shared.network

import com.tkolymp.shared.ServiceLocator
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class GraphQlClientImpl(private val httpClient: HttpClient, private val endpoint: String) : IGraphQlClient {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun post(query: String, variables: JsonObject?): JsonElement {
        val body = buildJsonObject {
            put("query", JsonPrimitive(query))
            if (variables != null) put("variables", variables)
        }

        val token = try { ServiceLocator.authService.getToken() } catch (_: Throwable) { null }

        val resp: JsonElement = httpClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            header("x-tenant-id", "1")
            if (token != null) header("Bearer", token)
            setBody(body)
        }.body()

        return resp
    }
}
