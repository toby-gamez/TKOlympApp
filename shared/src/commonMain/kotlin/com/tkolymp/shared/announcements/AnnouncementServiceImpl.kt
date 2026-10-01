package com.tkolymp.shared.announcements

import com.tkolymp.shared.network.IGraphQlClient
import com.tkolymp.shared.viewmodels.AppError
import com.tkolymp.shared.viewmodels.DataResult
import kotlinx.coroutines.CancellationException
import com.tkolymp.shared.cache.CacheService
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.json.*

private const val SITE_ORIGIN = "https://tkolymp.cz"

private val imgSrcRegex = Regex("(<img\\b[^>]*?\\bsrc\\s*=\\s*)([\"'])(.*?)\\2", RegexOption.IGNORE_CASE)

private const val CZECH_FROM = "áčďéěíňóřšťúůýžÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ"
private const val CZECH_TO = "acdeeinorstuuyzACDEEINORSTUUYZ"

/** Comparison key that ignores Unicode normalization form (NFC vs NFD) and space flavours. */
private fun fileKey(s: String): String = buildString {
    for (c in s) {
        when {
            c.code in 0x300..0x36F -> {}
            c == '\u00A0' || c == '\u202F' -> append(' ')
            else -> { val i = CZECH_FROM.indexOf(c); append(if (i >= 0) CZECH_TO[i] else c) }
        }
    }
}

private fun percentEncodePath(path: String): String = buildString {
    for (b in path.encodeToByteArray()) {
        val c = b.toInt() and 0xFF
        val ch = c.toChar()
        if (c < 128 && (ch.isLetterOrDigit() || ch in "-._~/")) append(ch)
        else { append('%'); append(c.toString(16).uppercase().padStart(2, '0')) }
    }
}

/** Attachment file URLs (exact server spelling) keyed by [fileKey] of the path. */
private fun attachmentUrls(obj: JsonObject): Map<String, String> {
    val nodes = ((obj["attachments"] as? JsonObject)?.get("nodes") as? JsonArray) ?: return emptyMap()
    return nodes.mapNotNull { n ->
        ((n as? JsonObject)?.get("file") as? JsonObject)?.get("url")?.let { (it as? JsonPrimitive)?.contentOrNull }
    }.associateBy { fileKey(it) }
}

/**
 * Makes image URLs in announcement HTML loadable: absolute, percent-encoded, and – when the
 * announcement has a matching attachment – using the attachment's exact file path (the HTML often
 * differs from the stored filename by Unicode normalization or `&nbsp;`).
 */
internal fun normalizeBody(html: String, attachments: Map<String, String> = emptyMap()): String = imgSrcRegex.replace(html) { m ->
    var url = m.groupValues[3].replace("&nbsp;", "\u00A0").replace("&amp;", "&")
    if (url.contains('%')) url = percentDecode(url)
    if (url.startsWith("/") && !url.startsWith("//")) {
        url = SITE_ORIGIN + percentEncodePath(attachments[fileKey(url)] ?: url)
    }
    m.groupValues[1] + m.groupValues[2] + url + m.groupValues[2]
}

private fun percentDecode(s: String): String {
    if (!s.startsWith("/")) return s
    val out = ArrayList<Byte>()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '%' && i + 2 < s.length + 0 && s.substring(i + 1, i + 3).toIntOrNull(16) != null) {
            out.add(s.substring(i + 1, i + 3).toInt(16).toByte()); i += 3
        } else { out.addAll(c.toString().encodeToByteArray().toList()); i++ }
    }
    return out.toByteArray().decodeToString()
}

private fun authorNameOf(obj: JsonObject): Author? {
    val name = (obj["authorName"] as? JsonPrimitive)?.contentOrNull?.trim()
    return if (name.isNullOrBlank()) null else Author(id = null, uJmeno = name, uPrijmeni = null)
}

class AnnouncementServiceImpl(
    private val client: IGraphQlClient,
    private val cache: CacheService
) : IAnnouncementService {
    private val query = """
        query MyQuery(${'$'}sticky: Boolean) { announcements(condition: { isSticky: ${'$'}sticky }) { nodes { body createdAt id isSticky status title authorName author { id uJmeno uPrijmeni } updatedAt attachments { nodes { inline file { id name contentType url objectKey isPublic } } } } } }
    """.trimIndent()

    override suspend fun getAnnouncements(sticky: Boolean): DataResult<List<Announcement>> {
        return try {
            val cacheKey = "announcements_sticky_$sticky"
            cache.get<List<Announcement>>(cacheKey)?.let { return DataResult.Success(it) }
            val variables = buildJsonObject { put("sticky", JsonPrimitive(sticky)) }
            val resp = client.post(query, variables)
            val data = resp.jsonObject["data"] ?: return DataResult.Error(AppError.network("Malformed response: missing data"))
            val announcements = (data.jsonObject["announcements"] ?: return DataResult.Error(AppError.network("Malformed response: missing announcements")))
            val nodes = announcements.jsonObject["nodes"] ?: return DataResult.Error(AppError.network("Malformed response: missing nodes"))
            if (nodes is JsonArray) {
                val result = nodes.mapNotNull { elem ->
                    try {
                        val obj = elem.jsonObject
                        val id = (obj["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                        val title = (obj["title"] as? JsonPrimitive)?.contentOrNull
                        val body = (obj["body"] as? JsonPrimitive)?.contentOrNull?.let { normalizeBody(it, attachmentUrls(obj)) }
                        val createdAt = (obj["createdAt"] as? JsonPrimitive)?.contentOrNull
                        val updatedAt = (obj["updatedAt"] as? JsonPrimitive)?.contentOrNull
                        val isSticky = obj["isSticky"]?.jsonPrimitive?.booleanOrNull ?: false
                        val isVisible = obj["status"]?.jsonPrimitive?.contentOrNull == "PUBLISHED"
                        val authorObj = obj["author"] as? JsonObject
                        val author = authorObj?.let {
                            Author(
                                id = (it["id"] as? JsonPrimitive)?.contentOrNull,
                                uJmeno = (it["uJmeno"] as? JsonPrimitive)?.contentOrNull,
                                uPrijmeni = (it["uPrijmeni"] as? JsonPrimitive)?.contentOrNull
                            )
                        } ?: authorNameOf(obj)
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
                if (result.isNotEmpty()) {
                    try { cache.put(cacheKey, result, ttl = 2.minutes) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
                }
                return DataResult.Success(result)
            }
            DataResult.Success(emptyList())
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            DataResult.Error(AppError.network(ex.message))
        }
    }

    private val singleQuery = """
        query MyQuery(${'$'}id: BigInt!) { announcement(id: ${'$'}id) { id title body createdAt updatedAt isSticky status authorName author { id uJmeno uPrijmeni } attachments { nodes { inline file { name contentType url } } } } }
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
                val idStr = (obj["id"] as? JsonPrimitive)?.contentOrNull ?: return DataResult.Error(AppError.notFound("Announcement has no id"))
                val title = (obj["title"] as? JsonPrimitive)?.contentOrNull
                val body = (obj["body"] as? JsonPrimitive)?.contentOrNull?.let { normalizeBody(it, attachmentUrls(obj)) }
                val createdAt = (obj["createdAt"] as? JsonPrimitive)?.contentOrNull
                val updatedAt = (obj["updatedAt"] as? JsonPrimitive)?.contentOrNull
                val isSticky = obj["isSticky"]?.jsonPrimitive?.booleanOrNull ?: false
                val isVisible = obj["status"]?.jsonPrimitive?.contentOrNull == "PUBLISHED"
                val authorObj = obj["author"] as? JsonObject
                val author = authorObj?.let {
                    Author(
                        id = (it["id"] as? JsonPrimitive)?.contentOrNull,
                        uJmeno = (it["uJmeno"] as? JsonPrimitive)?.contentOrNull,
                        uPrijmeni = (it["uPrijmeni"] as? JsonPrimitive)?.contentOrNull
                    )
                } ?: authorNameOf(obj)
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
