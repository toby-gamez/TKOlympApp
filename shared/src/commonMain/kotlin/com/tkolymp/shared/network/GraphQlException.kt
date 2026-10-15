package com.tkolymp.shared.network

import kotlinx.serialization.json.JsonElement

/**
 * The server answered, but with GraphQL `errors` (HTTP 200 or an error status carrying a GraphQL body).
 * [data] holds whatever partial `data` came back alongside the errors, so a caller that can live with
 * missing fields may opt in to using it instead of failing the whole screen.
 */
class GraphQlException(message: String, val data: JsonElement? = null) : Exception(message)

/** The server answered with a non-success HTTP status and no usable GraphQL error payload (e.g. an HTML 502 page). */
class GraphQlHttpException(val status: Int) : Exception("HTTP $status")
