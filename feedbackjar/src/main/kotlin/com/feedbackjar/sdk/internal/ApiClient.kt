package com.feedbackjar.sdk.internal

import com.feedbackjar.sdk.CommentResponse
import com.feedbackjar.sdk.FeedbackComment
import com.feedbackjar.sdk.FeedbackCommentListResult
import com.feedbackjar.sdk.FeedbackListResult
import com.feedbackjar.sdk.FeedbackPost
import com.feedbackjar.sdk.FeedbackResponse
import com.feedbackjar.sdk.VoteState
import com.feedbackjar.sdk.WidgetConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

@Serializable
private data class SubmitRequest(
    val content: String,
    val email: String? = null,
    val userId: String? = null,
    val userName: String? = null,
    val metadata: JsonObject,
)

@Serializable
private data class SubmitResponseBody(
    val success: Boolean,
    val postId: String,
    val title: String,
    val type: String,
    val boardId: String,
)

@Serializable
private data class PostBody(
    val id: String,
    val title: String,
    val content: String,
    val type: String,
    val status: String,
    val slug: String,
    val boardId: String,
    val voteCount: Int,
    val commentCount: Int,
    val upvotes: Int,
    val hasVoted: Boolean = false,
    val authorName: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
private data class ListResponseBody(
    val posts: List<PostBody>,
    val nextCursor: String? = null,
)

@Serializable
private data class ConfigResponseBody(
    val collectName: Boolean = false,
    val collectEmail: Boolean = false,
    val allowVotes: Boolean = false,
    val allowComments: Boolean = false,
)

@Serializable
private data class ErrorBody(
    val error: String? = null,
)

@Serializable
private data class VoteStateBody(
    val upvotes: Int,
    val hasVoted: Boolean = false,
)

@Serializable
private data class CommentBody(
    val id: String,
    val content: String,
    val authorName: String,
    val authorRole: String? = null,
    val isBot: Boolean = false,
    val parentId: String? = null,
    val createdAt: String,
    val replies: List<CommentBody> = emptyList(),
)

@Serializable
private data class CommentListResponseBody(
    val comments: List<CommentBody> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
private data class CreateCommentRequest(
    val content: String,
    val parentId: String? = null,
    val name: String? = null,
    val email: String? = null,
)

@Serializable
private data class CreateCommentResponseBody(
    val id: String,
)

@Serializable
private data class IdentifyRequest(
    val name: String? = null,
    val email: String? = null,
)

internal class ApiClient(private val appId: String? = null) {

    // Interceptor adds `X-FeedbackJar-SDK` to every request in one place.
    private val http = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("X-FeedbackJar-SDK", SdkInfo.IDENTIFIER)
                    .build(),
            )
        }
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = "https://api.feedbackjar.com"

    private fun Request.Builder.withAppIdHeader(): Request.Builder {
        if (!appId.isNullOrBlank()) {
            addHeader("X-FeedbackJar-App-Id", appId)
        }
        return this
    }

    private fun Request.Builder.withAnonIdHeader(anonId: String?): Request.Builder {
        if (!anonId.isNullOrBlank()) {
            addHeader("X-FeedbackJar-Anon-Id", anonId)
        }
        return this
    }

    /** Turns a non-2xx response body into a [Throwable], surfacing `{"error": "..."}` verbatim. */
    private fun errorFrom(raw: String, code: Int, fallback: String): Throwable {
        val message = try {
            json.decodeFromString<ErrorBody>(raw).error
        } catch (_: Exception) {
            null
        }
        return IOException(message ?: "$fallback: HTTP $code")
    }

    private fun CommentBody.toModel(): FeedbackComment = FeedbackComment(
        id = id,
        content = content,
        authorName = authorName,
        authorRole = authorRole,
        isBot = isBot,
        parentId = parentId,
        createdAt = createdAt,
        replies = replies.map { it.toModel() },
    )

    fun submit(widgetId: String, content: String, email: String? = null, userName: String? = null, metadata: JsonObject): Result<FeedbackResponse> {
        val body = json.encodeToString(SubmitRequest(content = content, email = email, userName = userName, metadata = metadata))
            .toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url("$baseUrl/widget/$widgetId/submit")
            .post(body)
            .withAppIdHeader()
            .build()

        return try {
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Result.failure(errorFrom(raw, response.code, "Submit failed"))
                } else {
                    val parsed = json.decodeFromString<SubmitResponseBody>(raw)
                    Result.success(
                        FeedbackResponse(
                            postId = parsed.postId,
                            title = parsed.title,
                            type = parsed.type,
                            boardId = parsed.boardId,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listFeedback(
        widgetId: String,
        boardId: String?,
        limit: Int,
        cursor: String?,
        anonId: String?,
    ): Result<FeedbackListResult> {
        val url = buildString {
            append("$baseUrl/widget/$widgetId/posts?limit=$limit")
            if (boardId != null) append("&boardId=$boardId")
            if (cursor != null) append("&cursor=$cursor")
        }

        val request = Request.Builder().url(url).get()
            .withAppIdHeader()
            .withAnonIdHeader(anonId)
            .build()

        return try {
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Result.failure(errorFrom(raw, response.code, "List failed"))
                } else {
                    val parsed = json.decodeFromString<ListResponseBody>(raw)
                    Result.success(
                        FeedbackListResult(
                            posts = parsed.posts.map(::toFeedbackPost),
                            nextCursor = parsed.nextCursor,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun toFeedbackPost(it: PostBody): FeedbackPost = FeedbackPost(
        id = it.id,
        title = it.title,
        content = it.content,
        type = it.type,
        status = it.status,
        slug = it.slug,
        boardId = it.boardId,
        voteCount = it.voteCount,
        commentCount = it.commentCount,
        upvotes = it.upvotes,
        hasVoted = it.hasVoted,
        authorName = it.authorName,
        createdAt = it.createdAt,
        updatedAt = it.updatedAt,
    )

    /** One public post by id — resolves `#[title](postId)` mention jump-links. */
    fun getPost(widgetId: String, postId: String, anonId: String?): Result<FeedbackPost> {
        val request = Request.Builder()
            .url("$baseUrl/widget/$widgetId/posts/$postId")
            .get()
            .withAppIdHeader()
            .withAnonIdHeader(anonId)
            .build()

        return try {
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Result.failure(errorFrom(raw, response.code, "Post fetch failed"))
                } else {
                    Result.success(toFeedbackPost(json.decodeFromString<PostBody>(raw)))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getConfig(widgetId: String): Result<WidgetConfig> {
        val request = Request.Builder()
            .url("$baseUrl/widget/$widgetId/config")
            .get()
            .withAppIdHeader()
            .build()

        return try {
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Result.failure(errorFrom(raw, response.code, "Config fetch failed"))
                } else {
                    val parsed = json.decodeFromString<ConfigResponseBody>(raw)
                    Result.success(
                        WidgetConfig(
                            collectName = parsed.collectName,
                            collectEmail = parsed.collectEmail,
                            allowVotes = parsed.allowVotes,
                            allowComments = parsed.allowComments,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** `POST /widget/:id/posts/:postId/vote` or `.../unvote` — idempotent, anon-id header. */
    private fun voteRequest(
        widgetId: String,
        postId: String,
        action: String,
        anonId: String,
    ): Result<VoteState> {
        val request = Request.Builder()
            .url("$baseUrl/widget/$widgetId/posts/$postId/$action")
            .post(ByteArray(0).toRequestBody())
            .withAppIdHeader()
            .withAnonIdHeader(anonId)
            .build()

        return try {
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Result.failure(errorFrom(raw, response.code, "$action failed"))
                } else {
                    val parsed = json.decodeFromString<VoteStateBody>(raw)
                    Result.success(VoteState(upvotes = parsed.upvotes, hasVoted = parsed.hasVoted))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun vote(widgetId: String, postId: String, anonId: String): Result<VoteState> =
        voteRequest(widgetId, postId, "vote", anonId)

    fun unvote(widgetId: String, postId: String, anonId: String): Result<VoteState> =
        voteRequest(widgetId, postId, "unvote", anonId)

    fun getVoteState(widgetId: String, postId: String, anonId: String): Result<VoteState> {
        val request = Request.Builder()
            .url("$baseUrl/widget/$widgetId/posts/$postId/vote")
            .get()
            .withAppIdHeader()
            .withAnonIdHeader(anonId)
            .build()

        return try {
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Result.failure(errorFrom(raw, response.code, "Vote state fetch failed"))
                } else {
                    val parsed = json.decodeFromString<VoteStateBody>(raw)
                    Result.success(VoteState(upvotes = parsed.upvotes, hasVoted = parsed.hasVoted))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listComments(
        widgetId: String,
        postId: String,
        limit: Int,
        cursor: String?,
    ): Result<FeedbackCommentListResult> {
        val url = buildString {
            append("$baseUrl/widget/$widgetId/posts/$postId/comments?limit=$limit")
            if (cursor != null) append("&cursor=$cursor")
        }

        val request = Request.Builder().url(url).get().withAppIdHeader().build()

        return try {
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Result.failure(errorFrom(raw, response.code, "Comment list failed"))
                } else {
                    val parsed = json.decodeFromString<CommentListResponseBody>(raw)
                    Result.success(
                        FeedbackCommentListResult(
                            comments = parsed.comments.map { it.toModel() },
                            nextCursor = parsed.nextCursor,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun createComment(
        widgetId: String,
        postId: String,
        content: String,
        parentId: String? = null,
        name: String? = null,
        email: String? = null,
        anonId: String,
    ): Result<CommentResponse> {
        val body = json.encodeToString(
            CreateCommentRequest(content = content, parentId = parentId, name = name, email = email)
        ).toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url("$baseUrl/widget/$widgetId/posts/$postId/comments")
            .post(body)
            .withAppIdHeader()
            .withAnonIdHeader(anonId)
            .build()

        return try {
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Result.failure(errorFrom(raw, response.code, "Comment failed"))
                } else {
                    val parsed = json.decodeFromString<CreateCommentResponseBody>(raw)
                    Result.success(CommentResponse(id = parsed.id))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun identify(
        widgetId: String,
        name: String? = null,
        email: String? = null,
        anonId: String,
    ): Result<Unit> {
        val body = json.encodeToString(IdentifyRequest(name = name, email = email))
            .toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url("$baseUrl/widget/$widgetId/identify")
            .post(body)
            .withAppIdHeader()
            .withAnonIdHeader(anonId)
            .build()

        return try {
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Result.failure(errorFrom(raw, response.code, "Identify failed"))
                } else {
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
