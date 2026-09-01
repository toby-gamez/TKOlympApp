package com.tkolymp.shared.changelog

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.tkolymp.shared.json.AppJson
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
)

data class ChangelogRelease(
    val tagName: String,
    val title: String,
    val body: String,
    val publishedAt: String?,
    val author: String = "Tobias Heneman",
)

interface IChangelogService {
    suspend fun fetchReleases(): List<ChangelogRelease>
}

class ChangelogService(private val httpClient: HttpClient) : IChangelogService {

    private val repoOwner = "toby-gamez"
    private val repoName = "TKOlympApp"

    override suspend fun fetchReleases(): List<ChangelogRelease> {
        return try {
            val response = httpClient.get("https://api.github.com/repos/$repoOwner/$repoName/releases") {
                header("Accept", "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
            }
            if (!response.status.isSuccess()) return emptyList()
            val raw = response.bodyAsText()
            val releases = AppJson.decodeFromString(ListSerializer(GitHubRelease.serializer()), raw)
            releases
                .filter { !it.draft }
                .map { r ->
                    ChangelogRelease(
                        tagName = r.tagName,
                        title = r.name?.takeIf { it.isNotBlank() } ?: r.tagName,
                        body = r.body?.trim() ?: "",
                        publishedAt = r.publishedAt,
                        author = "Tobias Heneman",
                    )
                }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }
}
