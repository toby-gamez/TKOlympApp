package com.tkolymp.shared.network

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

interface IGraphQlClient {
    suspend fun post(query: String, variables: JsonObject? = null): JsonElement
}
