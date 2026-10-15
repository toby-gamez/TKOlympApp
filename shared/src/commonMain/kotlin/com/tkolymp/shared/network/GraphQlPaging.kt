package com.tkolymp.shared.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Fetches every node of `data.<connection>.nodes` by paging with `$first` / `$offset` (the operation must
 * declare both variables). Avoids both unbounded responses and silent truncation at a fixed `first:`.
 *
 * Returns null when the first page has no `nodes` array (malformed response); a later malformed page ends
 * the loop and returns what was collected so far.
 */
suspend fun IGraphQlClient.postAllNodes(
    query: String,
    connection: String,
    variables: JsonObject = JsonObject(emptyMap()),
    pageSize: Int = GRAPHQL_PAGE_SIZE,
    maxPages: Int = GRAPHQL_MAX_PAGES,
): List<JsonElement>? {
    val all = mutableListOf<JsonElement>()
    for (page in 0 until maxPages) {
        val vars = buildJsonObject {
            variables.forEach { (k, v) -> put(k, v) }
            put("first", JsonPrimitive(pageSize))
            put("offset", JsonPrimitive(page * pageSize))
        }
        val resp = post(query, vars)
        val nodes = ((resp as? JsonObject)?.get("data") as? JsonObject)
            ?.get(connection).let { it as? JsonObject }
            ?.get("nodes") as? JsonArray
        if (nodes == null) return if (page == 0) null else all
        all.addAll(nodes)
        if (nodes.size < pageSize) break
    }
    return all
}

const val GRAPHQL_PAGE_SIZE = 200
const val GRAPHQL_MAX_PAGES = 50
