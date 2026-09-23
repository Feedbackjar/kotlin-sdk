package com.feedbackjar.sdk

import android.content.Context
import com.feedbackjar.sdk.internal.AnonId
import com.feedbackjar.sdk.internal.ApiClient
import com.feedbackjar.sdk.internal.MetadataCollector
import com.feedbackjar.sdk.internal.toJsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * FeedbackJar Android SDK.
 *
 * Usage:
 * ```kotlin
 * // In Application.onCreate or Activity.onCreate:
 * FeedbackJar.init(context, "your-widget-id")
 *
 * // Anonymous submission (suspend):
 * val result = FeedbackJar.submit("Love the dark mode!")
 *
 * // Anonymous submission (callback):
 * FeedbackJar.submit("Love the dark mode!") { result ->
 *     result.onSuccess { response -> Log.d("FJ", "Posted: ${response.postId}") }
 *     result.onFailure { error -> Log.e("FJ", "Failed", error) }
 * }
 *
 * // List public feedbacks:
 * val result = FeedbackJar.listFeedback()
 *
 * // Check whether the org asks for name/email before showing your own form fields:
 * val config = FeedbackJar.getConfig().getOrNull()
 * if (config?.collectEmail == true) { /* show an email field */ }
 *
 * // Name/email passed to submit() are remembered automatically and reused on later
 * // calls. Manage them directly with:
 * FeedbackJar.setIdentity(name = "Ada", email = "ada@example.com")
 * val identity = FeedbackJar.getIdentity()
 * FeedbackJar.clearIdentity() // e.g. on logout
 * ```
 */
object FeedbackJar {

    private const val PREFS_NAME = "com.feedbackjar.sdk.prefs"
    private const val PREF_NAME_KEY = "identity_name"
    private const val PREF_EMAIL_KEY = "identity_email"

    private var widgetId: String? = null
    private var appContext: Context? = null
    private var client: ApiClient? = null

    /**
     * Initialize the SDK. Call once before any other method, typically in Application.onCreate.
     */
    fun init(context: Context, widgetId: String) {
        this.appContext = context.applicationContext
        this.widgetId = widgetId
        this.client = ApiClient(this.appContext?.packageName)
    }

    /**
     * Remember a submitter's name/email so future [submit] calls reuse them automatically.
     * Pass `null` for a field to leave it unchanged; use [clearIdentity] to remove both.
     *
     * When the SDK is initialized, the name/email are also synced to the server against
     * this install's anonymous id (best-effort, off the main thread) so guest votes and
     * comments show the right name and can be reconciled if the user later signs into the
     * web portal with that email. A failed sync never affects the local copy.
     */
    fun setIdentity(name: String? = null, email: String? = null) {
        val ctx = appContext ?: return
        val current = getIdentity()
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(PREF_NAME_KEY, name ?: current.name)
            .putString(PREF_EMAIL_KEY, email ?: current.email)
            .apply()

        val id = widgetId
        val apiClient = client
        if (id != null && apiClient != null && (name != null || email != null)) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { apiClient.identify(id, name, email, AnonId.get(ctx)) }
            }
        }
    }

    /**
     * The currently remembered submitter identity, if any.
     */
    fun getIdentity(): FeedbackIdentity {
        val ctx = appContext ?: return FeedbackIdentity(null, null)
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return FeedbackIdentity(
            name = prefs.getString(PREF_NAME_KEY, null),
            email = prefs.getString(PREF_EMAIL_KEY, null),
        )
    }

    /**
     * Forget the remembered submitter identity (e.g. on user logout).
     */
    fun clearIdentity() {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /**
     * Submit feedback. Must be called from a coroutine.
     *
     * @param content    The feedback text.
     * @param email      Optional submitter email (for headless/anonymous submissions).
     * @param userName   Optional submitter name (for headless/anonymous submissions).
     * @param properties Optional custom key/value pairs (e.g. `"flavor" to BuildConfig.FLAVOR`)
     *                   merged into the auto-collected `app` metadata. Values should be
     *                   String, Number, or Boolean — nested maps/lists aren't supported.
     */
    suspend fun submit(
        content: String,
        email: String? = null,
        userName: String? = null,
        properties: Map<String, Any?>? = null,
    ): Result<FeedbackResponse> = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext notInitialized()
        val id = widgetId ?: return@withContext notInitialized()
        if (email != null || userName != null) {
            setIdentity(name = userName, email = email)
        }
        val identity = getIdentity()
        val metadata = MetadataCollector.collect(ctx)
        val finalMetadata = if (properties.isNullOrEmpty()) {
            metadata
        } else {
            buildJsonObject {
                metadata.forEach { (key, value) ->
                    if (key == "app" && value is JsonObject) {
                        put(key, buildJsonObject {
                            value.forEach { (k, v) -> put(k, v) }
                            properties.toJsonObject().forEach { (k, v) -> put(k, v) }
                        })
                    } else {
                        put(key, value)
                    }
                }
            }
        }
        val apiClient = client ?: return@withContext notInitialized()
        apiClient.submit(id, content, email ?: identity.email, userName ?: identity.name, finalMetadata)
    }

    /**
     * Submit feedback with a callback. Safe to call from the main thread.
     *
     * @param content    The feedback text.
     * @param email      Optional submitter email (for headless/anonymous submissions).
     * @param userName   Optional submitter name (for headless/anonymous submissions).
     * @param properties Optional custom key/value context sent alongside device metadata.
     * @param callback   Receives the result on completion.
     */
    fun submit(
        content: String,
        email: String? = null,
        userName: String? = null,
        properties: Map<String, Any?>? = null,
        callback: (Result<FeedbackResponse>) -> Unit,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            callback(submit(content, email, userName, properties))
        }
    }

    /**
     * List public feedbacks for this widget's organization.
     *
     * @param boardId optional — filter to a specific board
     * @param limit   max items per page (1–50, default 20)
     * @param cursor  pagination cursor from a previous [FeedbackListResult.nextCursor]
     */
    suspend fun listFeedback(
        boardId: String? = null,
        limit: Int = 20,
        cursor: String? = null,
    ): Result<FeedbackListResult> = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext notInitialized()
        val id = widgetId ?: return@withContext notInitialized()
        val apiClient = client ?: return@withContext notInitialized()
        apiClient.listFeedback(id, boardId, limit.coerceIn(1, 50), cursor, AnonId.get(ctx))
    }

    /**
     * List public feedbacks with a callback. Safe to call from the main thread.
     */
    fun listFeedback(
        boardId: String? = null,
        limit: Int = 20,
        cursor: String? = null,
        callback: (Result<FeedbackListResult>) -> Unit,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            callback(listFeedback(boardId, limit, cursor))
        }
    }

    /**
     * Fetch a single public post by id — used to resolve `#[title](postId)`
     * mention jump-links. Same visibility rules as [listFeedback].
     */
    suspend fun getPost(postId: String): Result<FeedbackPost> = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext notInitialized()
        val id = widgetId ?: return@withContext notInitialized()
        val apiClient = client ?: return@withContext notInitialized()
        apiClient.getPost(id, postId, AnonId.get(ctx))
    }

    /**
     * [getPost] with a callback. Safe to call from the main thread.
     */
    fun getPost(postId: String, callback: (Result<FeedbackPost>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch { callback(getPost(postId)) }
    }

    /**
     * Fetch this widget's organization config, including whether it asks submitters
     * for their name/email ("Ask for Name" / "Ask for Email" in the dashboard).
     *
     * Use this to decide whether your own submission UI should show those fields —
     * the SDK does not render any UI itself.
     */
    suspend fun getConfig(): Result<WidgetConfig> = withContext(Dispatchers.IO) {
        val id = widgetId ?: return@withContext notInitialized()
        val apiClient = client ?: return@withContext notInitialized()
        apiClient.getConfig(id)
    }

    /**
     * Fetch widget config with a callback. Safe to call from the main thread.
     */
    fun getConfig(callback: (Result<WidgetConfig>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            callback(getConfig())
        }
    }

    /**
     * Upvote a post as this install's anonymous guest. Idempotent — voting twice is a
     * no-op. Requires guest voting to be enabled for the project ([WidgetConfig.allowVotes]).
     * Must be called from a coroutine. Returns the new count and vote state.
     */
    suspend fun vote(postId: String): Result<VoteState> = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext notInitialized()
        val id = widgetId ?: return@withContext notInitialized()
        val apiClient = client ?: return@withContext notInitialized()
        apiClient.vote(id, postId, AnonId.get(ctx))
    }

    /** Upvote a post with a callback. Safe to call from the main thread. */
    fun vote(postId: String, callback: (Result<VoteState>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch { callback(vote(postId)) }
    }

    /**
     * Remove this install's upvote from a post. Idempotent. Must be called from a coroutine.
     */
    suspend fun unvote(postId: String): Result<VoteState> = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext notInitialized()
        val id = widgetId ?: return@withContext notInitialized()
        val apiClient = client ?: return@withContext notInitialized()
        apiClient.unvote(id, postId, AnonId.get(ctx))
    }

    /** Remove this install's upvote with a callback. Safe to call from the main thread. */
    fun unvote(postId: String, callback: (Result<VoteState>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch { callback(unvote(postId)) }
    }

    /**
     * Current upvote count and whether this install has voted on the post. Must be
     * called from a coroutine.
     */
    suspend fun getVoteState(postId: String): Result<VoteState> = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext notInitialized()
        val id = widgetId ?: return@withContext notInitialized()
        val apiClient = client ?: return@withContext notInitialized()
        apiClient.getVoteState(id, postId, AnonId.get(ctx))
    }

    /** Read a post's vote state with a callback. Safe to call from the main thread. */
    fun getVoteState(postId: String, callback: (Result<VoteState>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch { callback(getVoteState(postId)) }
    }

    /**
     * List public comments for a post (two-level threads). Anonymous — no identity
     * required. Must be called from a coroutine.
     *
     * @param postId the post to read comments for
     * @param limit  max items per page (1–50, default 20)
     * @param cursor pagination cursor from a previous [FeedbackCommentListResult.nextCursor]
     */
    suspend fun listComments(
        postId: String,
        limit: Int = 20,
        cursor: String? = null,
    ): Result<FeedbackCommentListResult> = withContext(Dispatchers.IO) {
        val id = widgetId ?: return@withContext notInitialized()
        val apiClient = client ?: return@withContext notInitialized()
        apiClient.listComments(id, postId, limit.coerceIn(1, 50), cursor)
    }

    /** List a post's public comments with a callback. Safe to call from the main thread. */
    fun listComments(
        postId: String,
        limit: Int = 20,
        cursor: String? = null,
        callback: (Result<FeedbackCommentListResult>) -> Unit,
    ) {
        CoroutineScope(Dispatchers.IO).launch { callback(listComments(postId, limit, cursor)) }
    }

    /**
     * Add a public comment (or reply, via [parentId]) as this install's anonymous guest.
     * Must be called from a coroutine.
     *
     * [name]/[email] fall back to the remembered identity ([setIdentity]) when omitted;
     * `email` is used only for reply notifications and is never auto-linked to a real
     * account. Requires guest comments to be enabled ([WidgetConfig.allowComments]).
     *
     * @param parentId a root comment's id to reply to it; replies to replies aren't allowed
     */
    suspend fun addComment(
        postId: String,
        content: String,
        parentId: String? = null,
        name: String? = null,
        email: String? = null,
    ): Result<CommentResponse> = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext notInitialized()
        val id = widgetId ?: return@withContext notInitialized()
        val apiClient = client ?: return@withContext notInitialized()
        val identity = getIdentity()
        apiClient.createComment(
            widgetId = id,
            postId = postId,
            content = content,
            parentId = parentId,
            name = name ?: identity.name,
            email = email ?: identity.email,
            anonId = AnonId.get(ctx),
        )
    }

    /** Add a public comment with a callback. Safe to call from the main thread. */
    fun addComment(
        postId: String,
        content: String,
        parentId: String? = null,
        name: String? = null,
        email: String? = null,
        callback: (Result<CommentResponse>) -> Unit,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            callback(addComment(postId, content, parentId, name, email))
        }
    }

    private fun <T> notInitialized(): Result<T> =
        Result.failure(IllegalStateException("FeedbackJar not initialized. Call FeedbackJar.init() first."))
}
