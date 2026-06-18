package com.tkolymp.shared.network

import com.tkolymp.shared.Logger
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * Thrown when the GraphQL server returns HTTP 200 but includes an `errors` array in the payload.
 * Without this check callers silently receive null/empty data instead of a failure signal.
 */

class GraphQlClientImpl(
    private val httpClient: HttpClient,
    private val endpoint: String,
    private val tenantId: String,
    /** Injected token provider — breaks the circular dependency with AuthService. */
    private val tokenProvider: suspend () -> String?,
) : IGraphQlClient {
    

    override suspend fun post(query: String, variables: JsonObject?): JsonElement {
        val body = buildJsonObject {
            put("query", JsonPrimitive(query))
            if (variables != null) put("variables", variables)
        }

        val token = tokenProvider()

        Logger.d("GraphQlClient", "posting to $endpoint, tokenPresent=${token != null}")
        val resp: JsonElement = httpClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            header("x-tenant-id", tenantId)
            if (token != null) header("Authorization", "Bearer $token")
            setBody(body)
        }.body()

        val errors = (resp as? JsonObject)?.get("errors") as? JsonArray
        if (!errors.isNullOrEmpty()) {
            val msg = errors.joinToString("; ") { el ->
                (el as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull ?: "error"
            }
            throw GraphQlException(msg)
        }

        return resp
    }
}
