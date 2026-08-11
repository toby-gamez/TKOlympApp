package com.tkolymp.shared.feedback

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import com.tkolymp.shared.Logger

enum class FeedbackType { BUG_REPORT, FEATURE_SUGGESTION }

interface IFeedbackService {
    suspend fun submit(type: FeedbackType, name: String, email: String, message: String): Result<Unit>
}

@Serializable
private data class CreateFeedbackRequest(
    val name: String,
    val email: String,
    val title: String,
    val message: String,
    val platform: String,
    val type: String,
)

/** Talks to the Tobiso.Web feedback endpoint (`POST {feedbackBaseUrl}/Feedback`) — a separate backend from the club GraphQL API. */
class FeedbackService(
    private val httpClient: HttpClient,
    private val feedbackBaseUrl: String,
    private val platformLabel: String,
) : IFeedbackService {

    override suspend fun submit(type: FeedbackType, name: String, email: String, message: String): Result<Unit> {
        return try {
            val typeWire = when (type) {
                FeedbackType.BUG_REPORT -> "Bug"
                FeedbackType.FEATURE_SUGGESTION -> "FeatureRequest"
            }
            val response = httpClient.post("$feedbackBaseUrl/Feedback") {
                contentType(ContentType.Application.Json)
                setBody(
                    CreateFeedbackRequest(
                        name = name,
                        email = email,
                        title = "",   // backend generates title from message when empty
                        message = message,
                        platform = platformLabel,
                        type = typeWire,
                    )
                )
            }
            if (response.status.isSuccess()) Result.success(Unit)
            else {
                val body = response.bodyAsText()
                Logger.e("FeedbackService", "submit failed: HTTP ${response.status.value} body=$body")
                Result.failure(Exception("HTTP ${response.status.value}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e("FeedbackService", "submit failed", e)
            Result.failure(e)
        }
    }
}
