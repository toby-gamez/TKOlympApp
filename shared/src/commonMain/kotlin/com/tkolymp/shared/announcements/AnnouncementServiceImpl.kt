package com.tkolymp.shared.announcements

import com.tkolymp.shared.network.IGraphQlClient
import com.tkolymp.shared.viewmodels.AppError
import com.tkolymp.shared.viewmodels.DataResult
import kotlinx.coroutines.CancellationException
import com.tkolymp.shared.cache.CacheService
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.json.*

class AnnouncementServiceImpl(
    private val client: IGraphQlClient,
    private val cache: CacheService
) : IAnnouncementService {
    private val query = """
        query MyQuery(${'$'}sticky: Boolean) { myAnnouncements(sticky: ${'$'}sticky) { nodes { body createdAt id isSticky isVisible title author { id uJmeno uPrijmeni } updatedAt } } }
    """.trimIndent()

    override suspend fun getAnnouncements(sticky: Boolean): DataResult<List<Announcement>> {
        return try {
            val cacheKey = "announcements_sticky_$sticky"
            cache.get<List<Announcement>>(cacheKey)?.let { return DataResult.Success(it) }
            val variables = buildJsonObject { put("sticky", JsonPrimitive(sticky)) }
            val resp = client.post(query, variables)
            val data = resp.jsonObject["data"] ?: return DataResult.Success(emptyList())
            val myAnnouncements = (data.jsonObject["myAnnouncements"] ?: return DataResult.Success(emptyList()))
            val nodes = myAnnouncements.jsonObject["nodes"] ?: return DataResult.Success(emptyList())
            if (nodes is JsonArray) {
                val result = nodes.mapNotNull { elem ->
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
                    } catch (e: CancellationException) { throw e } catch (t: Exception) {
                        null
                    }
                }
                try { cache.put(cacheKey, result, ttl = 2.minutes) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
                return DataResult.Success(result)
            }
            DataResult.Success(emptyList())
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            DataResult.Error(AppError.network(ex.message))
        }
    }

    private val singleQuery = """
        query MyQuery(${'$'}id: BigInt!) { announcement(id: ${'$'}id) { id title body createdAt updatedAt isVisible author { uJmeno uPrijmeni } } }
    """.trimIndent()

    override suspend fun getAnnouncementById(id: Long, forceRefresh: Boolean): DataResult<Announcement> {
        return try {
            val cacheKey = "announcement_${'$'}id"
            if (!forceRefresh) cache.get<Announcement>(cacheKey)?.let { return DataResult.Success(it) }
            val variables = buildJsonObject { put("id", JsonPrimitive(id)) }
            val resp = client.post(singleQuery, variables)
            val data = resp.jsonObject["data"] ?: return DataResult.Error(AppError.notFound("Announcement not found"))
            val ann = (data.jsonObject["announcement"] ?: return DataResult.Error(AppError.notFound("Announcement not found")))
            val obj = ann.jsonObject
            try {
                val idStr = obj["id"]?.jsonPrimitive?.contentOrNull ?: return DataResult.Error(AppError.notFound("Announcement has no id"))
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
                val announcement = Announcement(
                    id = idStr,
                    title = title,
                    body = body,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    isSticky = isSticky,
                    isVisible = isVisible,
                    author = author
                )
                try { cache.put(cacheKey, announcement, ttl = 5.minutes) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
                DataResult.Success(announcement)
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
                DataResult.Error(AppError.generic("Failed to parse announcement"))
            }
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            DataResult.Error(AppError.network(ex.message))
        }
    }
}
