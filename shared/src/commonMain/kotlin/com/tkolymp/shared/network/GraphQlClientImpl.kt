package com.tkolymp.shared.network

import com.tkolymp.shared.Logger
import com.tkolymp.shared.graphql.GraphQlOperations
import com.tkolymp.shared.json.AppJson
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class GraphQlClientImpl(
    private val httpClient: HttpClient,
    private val endpoint: String,
    private val tenantId: String,
    /** Injected token provider — breaks the circular dependency with AuthService. */
    private val tokenProvider: suspend () -> String?,
    /**
     * Called once when a request that carried a token is rejected as unauthenticated/expired. Return true if the
     * token was renewed, which makes the client retry the request once with the new token.
     */
    private val onUnauthorized: (suspend () -> Boolean)? = null,
) : IGraphQlClient {

    override suspend fun post(query: String, variables: JsonObject?): JsonElement {
        val token = tokenProvider()
        val first = execute(query, variables, token)
        if (first !is Outcome.Unauthorized) return first.unwrap()

        // Login/Refresh are how a token is obtained, so refreshing in response to them would recurse.
        val renewable = token != null && onUnauthorized != null && query != GraphQlOperations.Refresh && query != GraphQlOperations.Login
        if (!renewable || onUnauthorized?.invoke() != true) return first.unwrap()
        Logger.d("GraphQlClient", "token rejected; retrying once after refresh")
        return execute(query, variables, tokenProvider()).unwrap()
    }

    private sealed interface Outcome {
        data class Ok(val body: JsonElement) : Outcome
        data class Failed(val error: Exception) : Outcome
        data class Unauthorized(val error: Exception) : Outcome

        fun unwrap(): JsonElement = when (this) {
            is Ok -> body
            is Failed -> throw error
            is Unauthorized -> throw error
        }
    }

    private suspend fun execute(query: String, variables: JsonObject?, token: String?): Outcome {
        val body = buildJsonObject {
            put("query", JsonPrimitive(query))
            if (variables != null) put("variables", variables)
        }

        Logger.d("GraphQlClient", "posting to $endpoint, tokenPresent=${token != null}")
        val response = httpClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            header("x-tenant-id", tenantId)
            if (token != null) header("Authorization", "Bearer $token")
            setBody(body.toString())
        }
        val status = response.status.value
        // Not `.body<JsonElement>()`: an HTML error page (proxy 502, WAF 403) must surface as an HTTP error, not a parse crash.
        val resp = runCatching { AppJson.parseToJsonElement(response.bodyAsText()) }.getOrNull() as? JsonObject
        val errors = (resp?.get("errors") as? JsonArray)?.takeIf { it.isNotEmpty() }

        if (errors != null) {
            val msg = errors.joinToString("; ") { el -> (el as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull ?: "error" }
            val ex = GraphQlException(msg, resp["data"]?.takeIf { it !is JsonNull })
            return if (status == 401 || looksUnauthenticated(msg)) Outcome.Unauthorized(ex) else Outcome.Failed(ex)
        }
        if (resp == null || !response.status.isSuccess()) {
            val ex = GraphQlHttpException(status)
            return if (status == 401) Outcome.Unauthorized(ex) else Outcome.Failed(ex)
        }
        return Outcome.Ok(resp)
    }

    private fun looksUnauthenticated(message: String): Boolean {
        val m = message.lowercase()
        return "jwt expired" in m || "jwt malformed" in m || "invalid signature" in m || "invalid token" in m || "unauthenticated" in m
    }
}
