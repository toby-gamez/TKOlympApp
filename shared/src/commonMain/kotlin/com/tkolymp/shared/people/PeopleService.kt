package com.tkolymp.shared.people

import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.network.IGraphQlClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class PersonName(val firstName: String?, val lastName: String?)

class PeopleService(private val client: IGraphQlClient = ServiceLocator.graphQlClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchPersonName(personId: String): PersonName? {
        val query = """
            query MyQuery {
              person(id: "$personId") {
                lastName
                firstName
              }
            }
        """.trimIndent()

        val el: JsonElement = try { client.post(query) } catch (_: Exception) { return null }
        val personObj = (el as? JsonObject)
            ?.get("data")?.let { it as? JsonObject }
            ?.get("person")?.let { it as? JsonObject }

        val first = personObj?.get("firstName")?.jsonPrimitive?.contentOrNull
        val last = personObj?.get("lastName")?.jsonPrimitive?.contentOrNull
        return if (first == null && last == null) null else PersonName(first, last)
    }

    suspend fun fetchPersonDisplayName(personId: String, markMe: Boolean = false): String? {
        val p = fetchPersonName(personId) ?: return null
        val name = listOfNotNull(p.firstName, p.lastName).joinToString(" ")
        return if (name.isBlank()) null else if (markMe) "$name (já)" else name
    }

    suspend fun fetchCoupleDisplayName(coupleId: String): String? {
        val query = """
            query MyQuery {
              couple(id: "$coupleId") {
                man { lastName firstName }
                woman { firstName lastName }
              }
            }
        """.trimIndent()

        val el: JsonElement = try { client.post(query) } catch (_: Exception) { return null }
        val coupleObj = (el as? JsonObject)
            ?.get("data")?.let { it as? JsonObject }
            ?.get("couple")?.let { it as? JsonObject }

        val manObj = coupleObj?.get("man") as? JsonObject
        val womanObj = coupleObj?.get("woman") as? JsonObject

        val manLast = manObj?.get("lastName")?.jsonPrimitive?.contentOrNull
        val womanLast = womanObj?.get("lastName")?.jsonPrimitive?.contentOrNull

        return when {
            !manLast.isNullOrBlank() && !womanLast.isNullOrBlank() -> "$manLast - $womanLast"
            else -> {
                val manName = listOfNotNull(manObj?.get("firstName")?.jsonPrimitive?.contentOrNull, manLast).joinToString(" ").trim()
                val womanName = listOfNotNull(womanObj?.get("firstName")?.jsonPrimitive?.contentOrNull, womanLast).joinToString(" ").trim()
                val parts = listOfNotNull(manName.takeIf { it.isNotBlank() }, womanName.takeIf { it.isNotBlank() })
                if (parts.isEmpty()) null else parts.joinToString(" - ")
            }
        }
    }
}
