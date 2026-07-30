package com.tkolymp.shared.event

import com.tkolymp.shared.Logger
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.network.IGraphQlClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Fetches per-person event attendance records from the API. */
class AttendanceRepository(
    private val client: IGraphQlClient = ServiceLocator.graphQlClient
) {
    /**
     * Fetches attendance statuses for the given person from the GraphQL API.
     * Returns a map of instanceId (Long) → status string (ATTENDED, NOT_EXCUSED, UNKNOWN, CANCELLED).
     */
    suspend fun fetchAttendanceStatuses(personId: String): Map<Long, String> {
        val idLong = personId.toLongOrNull()
        val query = if (idLong != null)
            "query MyQuery(\$id: BigInt!) { person(id: \$id) { eventAttendancesList { status instanceId } } }"
        else
            "query MyQuery(\$id: String!) { person(id: \$id) { eventAttendancesList { status instanceId } } }"
        val variables = buildJsonObject {
            if (idLong != null) put("id", JsonPrimitive(idLong))
            else put("id", JsonPrimitive(personId))
        }
        val resp = try {
            withContext(Dispatchers.Default) { client.post(query, variables) }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { return emptyMap() }
        val list = try {
            resp.jsonObject["data"]?.jsonObject?.get("person")?.jsonObject
                ?.get("eventAttendancesList")?.jsonArray ?: return emptyMap()
        } catch (_: Exception) { return emptyMap() }
        val result = mutableMapOf<Long, String>()
        list.forEach { el ->
            try {
                val obj = el.jsonObject
                val instId = obj["instanceId"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@forEach
                val status = obj["status"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                result[instId] = status
            } catch (_: Exception) {}
        }
        Logger.d("AttendanceRepository", "fetchAttendanceStatuses: loaded ${result.size} entries for person $personId")
        return result
    }
}
