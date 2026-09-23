# FeedbackJar Android SDK

A lightweight Android SDK for collecting user feedback. Drop in the prebuilt feedback board, or build your own UI on the data API — the SDK handles submission (enriched with device metadata), the public feedback list, guest votes and comments.

- **Min SDK:** 21 (Android 5.0)
- **Coordinates:** `com.feedbackjar:sdk`
- **License:** MIT

## Installation

Add Maven Central (most projects already have it) and the dependency.

`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

`build.gradle.kts` (app module):

```kotlin
dependencies {
    implementation("com.feedbackjar:sdk:1.5.1")
}
```

The SDK declares the `INTERNET` permission itself; nothing to add to your manifest.

## Setup

Initialize once before use — typically in your `Application.onCreate()` or your entry `Activity`. You need your **widget ID** from the FeedbackJar dashboard.

```kotlin
import com.feedbackjar.sdk.FeedbackJar

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FeedbackJar.init(this, "your-widget-id")
    }
}
```

## Prebuilt UI

Don't want to build a form? The SDK ships a full drop-in feedback board — list,
upvote, detail, comment threads and submission — in two flavours. Both read
`getConfig()` (hide vote controls when votes are off, hide the comment composer
when comments are off, show Name/Email on the form per your dashboard settings),
prefill the submitter identity, follow the system light/dark setting, and never
throw — failures show inline with the server message.

Call `FeedbackJar.init(...)` first, as usual.

### Views (zero dependency)

Framework views only — no RecyclerView, no ConstraintLayout, no Material, no
AndroidX UI. Nothing is added to your dependency tree.

```kotlin
import com.feedbackjar.sdk.ui.FeedbackJarView

val board = FeedbackJarView(context)
board.setAccentColor(Color.parseColor("#e5484d")) // optional, defaults to FeedbackJar red
board.boardId = "board-id"                          // optional filter
setContentView(board)

// let the board handle in-board Back navigation
override fun onBackPressed() {
    if (!board.onBackPressed()) super.onBackPressed()
}
```

Or launch the bundled full-screen Activity (already registered in the SDK manifest):

```kotlin
startActivity(FeedbackJarActivity.intent(context))
// with an accent:
startActivity(FeedbackJarActivity.intent(context, Color.parseColor("#e5484d")))
```

### Compose (opt-in)

Compose is a `compileOnly` dependency of the SDK, so the published `.aar` carries
**no transitive Compose dependency**. This composable only resolves in an app that
already uses Compose.

```kotlin
import androidx.compose.ui.graphics.Color
import com.feedbackjar.sdk.ui.compose.FeedbackJarBoard

FeedbackJarBoard(
    accentColor = Color(0xFFE5484D), // optional
    boardId = "board-id",            // optional filter
)
```

## Submitting feedback

Submissions can be anonymous, or include a submitter name/email if you collect them in your own form. Each submission automatically carries device metadata (Android version, device model, screen size, app version, locale).

### Coroutines (recommended)

```kotlin
lifecycleScope.launch {
    val result = FeedbackJar.submit(userText)
    result
        .onSuccess { response ->
            Log.d("FeedbackJar", "Submitted: ${response.postId} (${response.type})")
            // show a success state in your UI
        }
        .onFailure { error ->
            Log.e("FeedbackJar", "Failed to submit", error)
            // show an error state
        }
}
```

### Callback (no coroutine needed)

Safe to call from the main thread — the network call runs off the main thread and the callback is invoked when done.

```kotlin
FeedbackJar.submit(userText) { result ->
    result.onSuccess { response -> /* ... */ }
    result.onFailure { error -> /* ... */ }
}
```

> Note: the server applies rate limiting (5 submissions per 15 minutes per IP). Handle the failure case in your UI.

## Custom properties

Attach your own key/value context to a submission — merged into the auto-collected `app` metadata (alongside `packageName`, `versionName`, `versionCode`). Values should be `String`, `Number`, or `Boolean`; nested maps/lists aren't supported.

```kotlin
FeedbackJar.submit(
    userText,
    properties = mapOf("flavor" to BuildConfig.FLAVOR, "plan" to "pro"),
)
```

## Checking whether to ask for name/email

The organization's dashboard settings ("Ask for Name" / "Ask for Email") control whether submitters should be prompted. The SDK doesn't render any UI itself, so read this before building your own form:

```kotlin
lifecycleScope.launch {
    val config = FeedbackJar.getConfig().getOrNull()
    showNameField = config?.collectName == true
    showEmailField = config?.collectEmail == true
    showVoteButton = config?.allowVotes == true      // guest upvoting enabled
    showCommentBox = config?.allowComments == true    // guest commenting enabled
}
```

## Remembering submitter identity

Name/email passed to `submit()` are automatically remembered and reused on later calls, so you only need to ask once. Manage this directly with `setIdentity` / `getIdentity` / `clearIdentity`:

```kotlin
FeedbackJar.setIdentity(name = "Ada Lovelace", email = "ada@example.com")

val identity = FeedbackJar.getIdentity()
println(identity.name)  // "Ada Lovelace"

// e.g. on logout
FeedbackJar.clearIdentity()
```

## Listing feedback

Fetch the public feedback feed for your organization. Supports pagination via a cursor.

### Coroutines

```kotlin
lifecycleScope.launch {
    val result = FeedbackJar.listFeedback(limit = 20)
    result.onSuccess { page ->
        page.posts.forEach { post ->
            println("${post.title} — ${post.upvotes} upvotes, ${post.status}")
        }
        // page.nextCursor is non-null when more pages exist
    }
}
```

### Pagination

```kotlin
var cursor: String? = null

suspend fun loadNextPage() {
    FeedbackJar.listFeedback(limit = 20, cursor = cursor).onSuccess { page ->
        render(page.posts)
        cursor = page.nextCursor   // pass this back in for the next page
    }
}
```

### Callback

```kotlin
FeedbackJar.listFeedback(limit = 20) { result ->
    result.onSuccess { page -> /* ... */ }
}
```

Each `FeedbackPost` carries `hasVoted` — whether this install's anonymous guest has
upvoted it — so you can render the vote button in the right state straight from the list.

## Voting

Guests can upvote posts without an account. Each install gets a random anonymous id
on first use (a fresh UUID, persisted in `SharedPreferences`, reset on reinstall or
clear-data — never a device id). Voting is idempotent. Requires guest voting to be
enabled for the project (`WidgetConfig.allowVotes`).

```kotlin
lifecycleScope.launch {
    FeedbackJar.vote(post.id).onSuccess { state ->
        render(state.upvotes, state.hasVoted)   // hasVoted == true
    }

    // Toggle off
    FeedbackJar.unvote(post.id).onSuccess { state -> render(state.upvotes, state.hasVoted) }

    // Read the current state (e.g. on a detail screen)
    FeedbackJar.getVoteState(post.id).onSuccess { state -> render(state.upvotes, state.hasVoted) }
}
```

Callback variants are available too and are main-thread safe:

```kotlin
FeedbackJar.vote(post.id) { result ->
    result.onSuccess { state -> render(state.upvotes, state.hasVoted) }
    result.onFailure { error -> /* e.g. "Guest voting is disabled for this project." */ }
}
```

> Note: the server rate-limits vote/unvote to 60 per minute per IP. Errors surface
> verbatim in `Result.failure` — nothing throws.

## Comments

Read a post's public comment thread (two levels deep) with no identity required:

```kotlin
lifecycleScope.launch {
    FeedbackJar.listComments(post.id, limit = 20).onSuccess { page ->
        page.comments.forEach { comment ->
            println("${comment.authorName}: ${comment.content}")
            comment.replies.forEach { reply -> println("  ↳ ${reply.authorName}: ${reply.content}") }
        }
        // page.nextCursor is non-null when more pages exist
    }
}
```

Add a comment or a reply as the anonymous guest. `name`/`email` fall back to the
remembered identity (`setIdentity`); `email` is used only for reply notifications and
is never auto-linked to a real account. Requires guest comments to be enabled
(`WidgetConfig.allowComments`).

```kotlin
lifecycleScope.launch {
    // Top-level comment
    FeedbackJar.addComment(post.id, "Would love this on tablets too!")
        .onSuccess { ref -> println("Posted comment ${ref.id}") }

    // Reply to a root comment (replies to replies aren't allowed)
    FeedbackJar.addComment(post.id, "Agreed!", parentId = rootComment.id)

    // Override the remembered identity for this one comment
    FeedbackJar.addComment(post.id, "Nice", name = "Ada", email = "ada@example.com")
}
```

Callback variants are available and main-thread safe:

```kotlin
FeedbackJar.addComment(post.id, userText) { result ->
    result.onSuccess { ref -> /* ... */ }
    result.onFailure { error -> /* e.g. "Guest comments are disabled for this project." */ }
}
```

> Note: the server rate-limits comment creation to 10 per minute per IP.

## Rich text

Both prebuilt UIs (Views and Compose) render light Markdown in post and comment
content — **bold**, *italic*, `code`, `[links](url)`, headings, `-`/`1.` lists,
`>` quotes, ``` fenced code — plus FeedbackJar mention tokens: `#[Post title](postId)`
and `@[Name](user:id)`. List-row previews are flattened to plain text.

Tapping a `#[…]` post reference opens that post's detail screen — resolved with
`getPost` when the post isn't already loaded. Links open the system browser;
`@[…]` mentions are styled but not linked. No new dependency — framework text
spans in the Views UI, `AnnotatedString` in Compose.

## API reference

### `FeedbackJar`

| Method | Description |
| --- | --- |
| `init(context, widgetId)` | Initialize the SDK. Call once before anything else. |
| `suspend submit(content, email?, userName?, properties?): Result<FeedbackResponse>` | Submit feedback, optionally with custom properties merged into `app` metadata. |
| `submit(content, email?, userName?, properties?, callback)` | Callback variant, main-thread safe. |
| `suspend listFeedback(boardId?, limit = 20, cursor?): Result<FeedbackListResult>` | List public feedback. `limit` is clamped to 1–50. |
| `listFeedback(boardId?, limit, cursor?, callback)` | Callback variant. |
| `suspend getPost(postId): Result<FeedbackPost>` | Fetch one public post — resolves `#[…]` mention jump-links. |
| `getPost(postId, callback)` | Callback variant. |
| `suspend getConfig(): Result<WidgetConfig>` | Fetch whether the org asks for name/email and allows guest votes/comments. |
| `getConfig(callback)` | Callback variant. |
| `suspend vote(postId): Result<VoteState>` | Upvote a post as the anonymous guest. Idempotent. |
| `vote(postId, callback)` | Callback variant. |
| `suspend unvote(postId): Result<VoteState>` | Remove the guest's upvote. Idempotent. |
| `unvote(postId, callback)` | Callback variant. |
| `suspend getVoteState(postId): Result<VoteState>` | Current upvote count and whether this install voted. |
| `getVoteState(postId, callback)` | Callback variant. |
| `suspend listComments(postId, limit = 20, cursor?): Result<FeedbackCommentListResult>` | List a post's public comments. `limit` is clamped to 1–50. |
| `listComments(postId, limit, cursor?, callback)` | Callback variant. |
| `suspend addComment(postId, content, parentId?, name?, email?): Result<CommentResponse>` | Add a comment or reply as the guest. `name`/`email` fall back to the remembered identity. |
| `addComment(postId, content, parentId?, name?, email?, callback)` | Callback variant. |
| `setIdentity(name?, email?)` | Remember a submitter's name/email for future `submit()` calls; also best-effort synced to the server. |
| `getIdentity(): FeedbackIdentity` | The currently remembered identity, if any. |
| `clearIdentity()` | Forget the remembered identity. |

### `FeedbackResponse`

```kotlin
data class FeedbackResponse(
    val postId: String,
    val title: String,    // AI-generated title for the submission
    val type: String,     // e.g. FEEDBACK, BUG, FEATURE_REQUEST
    val boardId: String,
)
```

### `FeedbackPost`

```kotlin
data class FeedbackPost(
    val id: String,
    val title: String,
    val content: String,
    val type: String,
    val status: String,       // OPEN, IN_PROGRESS, COMPLETED, ...
    val slug: String,
    val boardId: String,
    val voteCount: Int,
    val commentCount: Int,
    val upvotes: Int,
    val hasVoted: Boolean,    // this install's anonymous guest has upvoted this post
    val authorName: String?,
    val createdAt: String,    // ISO-8601
    val updatedAt: String,    // ISO-8601
)
```

### `FeedbackListResult`

```kotlin
data class FeedbackListResult(
    val posts: List<FeedbackPost>,
    val nextCursor: String?,  // null when there are no more pages
)
```

### `WidgetConfig`

```kotlin
data class WidgetConfig(
    val collectName: Boolean,    // org asks for the submitter's name
    val collectEmail: Boolean,   // org asks for the submitter's email
    val allowVotes: Boolean,     // guest upvoting is enabled for this project
    val allowComments: Boolean,  // guest commenting is enabled for this project
)
```

### `FeedbackIdentity`

```kotlin
data class FeedbackIdentity(
    val name: String?,
    val email: String?,
)
```

### `VoteState`

```kotlin
data class VoteState(
    val upvotes: Int,        // current upvote count
    val hasVoted: Boolean,   // this install's anonymous guest has upvoted
)
```

### `FeedbackComment`

```kotlin
data class FeedbackComment(
    val id: String,
    val content: String,
    val authorName: String,
    val authorRole: String?,          // "owner" / "admin" / "member" for team members, else null
    val isBot: Boolean,
    val parentId: String?,            // root comment's id when this is a reply, else null
    val createdAt: String,            // ISO-8601
    val replies: List<FeedbackComment>,  // empty for replies themselves (threads are two levels)
)
```

### `FeedbackCommentListResult`

```kotlin
data class FeedbackCommentListResult(
    val comments: List<FeedbackComment>,
    val nextCursor: String?,  // null when there are no more pages
)
```

### `CommentResponse`

```kotlin
data class CommentResponse(
    val id: String,  // the new comment's id
)
```

## Notes

- Feedback can be submitted anonymously, or with a name/email — the SDK never requires either.
- Name/email are persisted in `SharedPreferences` on-device (no extra dependency) so they survive app restarts.
- Votes and comments are attributed to a random per-install anonymous id (a UUID in `SharedPreferences`, not a device id). It resets on reinstall or clear-data, and `clearIdentity()` does not touch it.
- Private boards and non-public posts are never returned by `listFeedback` or `getPost`.
- Every request carries an `X-FeedbackJar-SDK: kotlin/<version>` header; submissions also include `sdk` / `sdkVersion` in metadata.
- All network calls return a Kotlin `Result`; nothing throws on network/HTTP errors.
