package com.tkolymp.shared.files

import com.tkolymp.shared.json.AppJson
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class FileUploadException(message: String) : Exception(message)

interface IFileUploader {
    /** Stores [bytes] on the club site and returns the new file's id (what e.g. `PersonPatch.imageId` expects). */
    suspend fun upload(bytes: ByteArray, contentType: String, fileName: String): String
}

/**
 * Uploads a file by POSTing its raw bytes to [uploadUrl] (the site's `/f`, the same route that later serves
 * `/f/{id}/{name}`), the way the website does: the login JWT as bearer token and as the `rozpisovnik` cookie, the
 * file name in `x-file-name`, and the site's own [origin] as `Origin`/`Referer` (the server rejects other origins
 * with 403 "invalid origin"; a native client sends none).
 */
class FileUploader(
    private val httpClient: HttpClient,
    private val uploadUrl: String,
    private val origin: String,
    private val tokenProvider: suspend () -> String?,
) : IFileUploader {

    override suspend fun upload(bytes: ByteArray, contentType: String, fileName: String): String {
        val token = tokenProvider() ?: throw FileUploadException("Not signed in")
        val response = httpClient.post(uploadUrl) {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Cookie, "rozpisovnik=$token")
            header(HttpHeaders.Origin, origin)
            header(HttpHeaders.Referrer, "$origin/")
            header("x-file-name", fileName)
            contentType(ContentType.parse(contentType))
            setBody(bytes)
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) throw FileUploadException("HTTP ${response.status.value}: ${body.take(200)}")
        return parseUploadedFileId(body, response.headers[HttpHeaders.Location])
            ?: throw FileUploadException("Upload succeeded (HTTP ${response.status.value}) but no file id in response: ${body.take(200)}")
    }
}

private val fileUrlId = Regex("/f/(\\d+)(?:/|$|\\?)")

/**
 * Finds the new file's id in an upload response. The response shape isn't documented, so this accepts a bare number,
 * JSON with an `id` (also nested under `file`/`data`), or a `/f/{id}/...` URL in a `url`/`Location` value.
 */
fun parseUploadedFileId(body: String, location: String? = null): String? {
    val trimmed = body.trim()
    if (trimmed.isNotEmpty() && trimmed.all { it.isDigit() }) return trimmed
    val json: JsonElement? = try { AppJson.parseToJsonElement(trimmed) } catch (_: Exception) { null }
    idFrom(json)?.let { return it }
    return location?.let { fileUrlId.find(it)?.groupValues?.get(1) }
}

private fun idFrom(element: JsonElement?): String? = when (element) {
    is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
    is JsonArray -> element.firstNotNullOfOrNull { idFrom(it) }
    is JsonObject -> {
        (element["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
            ?: (element["url"] as? JsonPrimitive)?.contentOrNull?.let { fileUrlId.find(it)?.groupValues?.get(1) }
            ?: listOf("file", "data", "upload").firstNotNullOfOrNull { idFrom(element[it]) }
    }
    else -> null
}
