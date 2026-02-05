package com.tkolymp.shared.announcements

import com.tkolymp.shared.ServiceLocator
import kotlinx.serialization.json.*

class AnnouncementServiceImpl : IAnnouncementService {
    private val query = """
        query MyQuery(${'$'}sticky: Boolean) { myAnnouncements(sticky: ${'$'}sticky) { nodes { body createdAt id isSticky isVisible title author { id uJmeno uPrijmeni } updatedAt } } }
    """.trimIndent()

    override suspend fun getAnnouncements(sticky: Boolean): List<Announcement> {
        val variables = buildJsonObject { put("sticky", JsonPrimitive(sticky)) }
        val resp = ServiceLocator.graphQlClient.post(query, variables)
        val data = resp.jsonObject["data"] ?: return emptyList()
        val myAnnouncements = (data.jsonObject["myAnnouncements"] ?: return emptyList())
        val nodes = myAnnouncements.jsonObject["nodes"] ?: return emptyList()
        if (nodes is JsonArray) {
            return nodes.mapNotNull { elem ->
                try {
                    val obj = elem.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val title = obj["title"]?.jsonPrimitive?.contentOrNull
                    val body = obj["body"]?.jsonPrimitive?.contentOrNull
                    val createdAt = obj["createdAt"]?.jsonPrimitive?.contentOrNull
                    val updatedAt = obj["updatedAt"]?.jsonPrimitive?.contentOrNull
                    val isSticky = obj["isSticky"]?.jsonPrimitive?.booleanOrNull ?: false
                    val isVisible = obj["isVisible"]?.jsonPrimitive?.booleanOrNull ?: false
                    val authorObj = obj["author"]?.jsonObject
                    val author = authorObj?.let {
                        Author(
                            id = it["id"]?.jsonPrimitive?.contentOrNull,
                            uJmeno = it["uJmeno"]?.jsonPrimitive?.contentOrNull,
                            uPrijmeni = it["uPrijmeni"]?.jsonPrimitive?.contentOrNull
                        )
                    }
                    Announcement(
                        id = id,
                        title = title,
                        body = body,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        isSticky = isSticky,
                        isVisible = isVisible,
                        author = author
                    )
                } catch (t: Throwable) {
                    null
                }
            }
        }
        return emptyList()
    }

    private val singleQuery = """
        query MyQuery(${'$'}id: BigInt!) { announcement(id: ${'$'}id) { id title body createdAt updatedAt isVisible author { uJmeno uPrijmeni } } }
    """.trimIndent()

    override suspend fun getAnnouncementById(id: Long): Announcement? {
        val variables = buildJsonObject { put("id", JsonPrimitive(id)) }
        val resp = ServiceLocator.graphQlClient.post(singleQuery, variables)
        val data = resp.jsonObject["data"] ?: return null
        val ann = (data.jsonObject["announcement"] ?: return null)
        val obj = ann.jsonObject
        return try {
            val idStr = obj["id"]?.jsonPrimitive?.contentOrNull ?: return null
            val title = obj["title"]?.jsonPrimitive?.contentOrNull
            val body = obj["body"]?.jsonPrimitive?.contentOrNull
            val createdAt = obj["createdAt"]?.jsonPrimitive?.contentOrNull
            val updatedAt = obj["updatedAt"]?.jsonPrimitive?.contentOrNull
            val isSticky = false
            val isVisible = obj["isVisible"]?.jsonPrimitive?.booleanOrNull ?: false
            val authorObj = obj["author"]?.jsonObject
            val author = authorObj?.let {
                Author(
                    id = null,
                    uJmeno = it["uJmeno"]?.jsonPrimitive?.contentOrNull,
                    uPrijmeni = it["uPrijmeni"]?.jsonPrimitive?.contentOrNull
                )
            }
            Announcement(
                id = idStr,
                title = title,
                body = body,
                createdAt = createdAt,
                updatedAt = updatedAt,
                isSticky = isSticky,
                isVisible = isVisible,
                author = author
            )
        } catch (_: Throwable) { null }
    }
}
