package com.feedbackjar.sdk.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feedbackjar.sdk.FeedbackComment
import com.feedbackjar.sdk.FeedbackJar
import com.feedbackjar.sdk.FeedbackPost
import com.feedbackjar.sdk.WidgetConfig
import com.feedbackjar.sdk.internal.richTextToPlainText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Opt-in Compose feedback board — list + upvote + detail + comments + submission,
 * built on the public [FeedbackJar] data API.
 *
 * Compose is a `compileOnly` dependency of the SDK: the published `.aar` carries no
 * transitive Compose dependency, so calling this only works in an app that already
 * uses Compose. Zero-dependency callers use `com.feedbackjar.sdk.ui.FeedbackJarView`.
 *
 * ```kotlin
 * FeedbackJarBoard(accentColor = Color(0xFFE5484D))
 * ```
 *
 * Requires [FeedbackJar.init] to have been called. Nothing throws — failures show inline.
 *
 * @param properties called each time the board's own "New" screen sends feedback, to get
 *   custom key/value pairs merged into the auto-collected metadata. Forwarded verbatim to
 *   [FeedbackJar.submit]'s `properties` parameter.
 * @param commentIdentity called each time a comment is sent from a post's detail screen, to
 *   get the name/email (`first`/`second`) to attach to it. Return `null`, or a `null` field,
 *   to fall back to the remembered identity (the default when this is omitted entirely).
 * @param resetIdentity a host-hoisted flag; flip it to `true` to forget the remembered
 *   submitter identity (e.g. on logout) and refresh the board as a clean anonymous guest.
 *   The board clears [FeedbackJar.clearIdentity], drops back to the board list and reloads
 *   it. Since this is read-only `State`, the host is responsible for flipping it back to
 *   `false` afterwards so a later reset can fire again.
 * @param openPostId a host-hoisted post id; set it to jump the board straight to that post's
 *   detail screen (e.g. from a push notification). Since this is read-only `State`, the host
 *   is responsible for clearing it back to `null` afterwards so a later jump can fire again.
 */
@Composable
fun FeedbackJarBoard(
    accentColor: Color = Color(0xFFE5484D),
    boardId: String? = null,
    properties: (() -> Map<String, Any?>?)? = null,
    commentIdentity: (() -> Pair<String?, String?>?)? = null,
    resetIdentity: State<Boolean>? = null,
    openPostId: State<String?>? = null,
) {
    val palette = if (isSystemInDarkTheme()) DarkPalette else LightPalette
    val theme = remember(palette, accentColor) { FjTheme(palette, accentColor) }

    var config by remember {
        mutableStateOf(
            WidgetConfig(collectName = false, collectEmail = false, allowVotes = false, allowComments = false),
        )
    }
    LaunchedEffect(Unit) { FeedbackJar.getConfig().onSuccess { config = it } }

    var screen by remember { mutableStateOf<Screen>(Screen.Board) }
    var reloadKey by remember { mutableStateOf(0) }
    val rootScope = rememberCoroutineScope()

    // Resolve a `#[title](postId)` mention and open that post's detail screen.
    fun openPost(postId: String) {
        rootScope.launch { FeedbackJar.getPost(postId).onSuccess { screen = Screen.Detail(it) } }
    }

    // A host flips this to true to log the submitter out — forget the remembered identity,
    // drop back to the board and reload it as a clean anonymous guest.
    LaunchedEffect(resetIdentity?.value) {
        if (resetIdentity?.value == true) {
            FeedbackJar.clearIdentity()
            screen = Screen.Board
            reloadKey++
        }
    }

    // A host sets this to a post id to jump straight to its detail screen, e.g. from a
    // push notification.
    LaunchedEffect(openPostId?.value) {
        openPostId?.value?.let { openPost(it) }
    }

    Box(Modifier.fillMaxSize().background(theme.bg)) {
        when (val s = screen) {
            Screen.Board -> BoardScreen(
                theme = theme,
                config = config,
                boardId = boardId,
                reloadKey = reloadKey,
                onOpen = { screen = Screen.Detail(it) },
                onNew = { screen = Screen.New },
            )

            // key() so a jump-link to another post gets fresh state and reloads
            // that post's comments instead of keeping the previous ones.
            is Screen.Detail -> key(s.post.id) {
                DetailScreen(
                    theme = theme,
                    config = config,
                    post = s.post,
                    commentIdentity = commentIdentity,
                    onBack = { screen = Screen.Board },
                    onPostPress = ::openPost,
                )
            }

            Screen.New -> NewFeedbackScreen(
                theme = theme,
                config = config,
                properties = properties,
                onCancel = { screen = Screen.Board },
                onDone = {
                    reloadKey++
                    screen = Screen.Board
                },
            )
        }
    }
}

private sealed interface Screen {
    data object Board : Screen
    data object New : Screen
    data class Detail(val post: FeedbackPost) : Screen
}

// ---- theme ----

internal data class FjPalette(
    val bg: Color,
    val text: Color,
    val textDim: Color,
    val divider: Color,
    val fieldBg: Color,
)

private val LightPalette = FjPalette(
    bg = Color(0xFFFFFFFF),
    text = Color(0xFF1A1A1A),
    textDim = Color(0xFF767676),
    divider = Color(0xFFE6E6E6),
    fieldBg = Color(0xFFF4F4F4),
)

private val DarkPalette = FjPalette(
    bg = Color(0xFF151515),
    text = Color(0xFFF2F2F2),
    textDim = Color(0xFF9A9A9A),
    divider = Color(0xFF2C2C2C),
    fieldBg = Color(0xFF242424),
)

internal class FjTheme(palette: FjPalette, val accent: Color) {
    val bg = palette.bg
    val text = palette.text
    val textDim = palette.textDim
    val divider = palette.divider
    val fieldBg = palette.fieldBg
}

private val BodySize = 15.sp
private val SmallSize = 13.sp
private val Radius = RoundedCornerShape(8.dp)

private fun relativeTime(iso: String): String {
    val then = runCatching {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        fmt.parse(iso.take(19))?.time
    }.getOrNull() ?: return ""
    val s = ((System.currentTimeMillis() - then) / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60}m"
        s < 86400 -> "${s / 3600}h"
        s < 604800 -> "${s / 86400}d"
        else -> "${s / 604800}w"
    }
}

private fun humanStatus(status: String): String {
    val s = status.replace('_', ' ').lowercase(Locale.US)
    return s.replaceFirstChar { it.uppercase(Locale.US) }
}

// ---- shared bits ----

@Composable
private fun HairlineDivider(theme: FjTheme, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(theme.divider))
}

@Composable
private fun Spinner(theme: FjTheme, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = theme.accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    theme: FjTheme,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        textStyle = TextStyle(color = theme.text, fontSize = BodySize),
        cursorBrush = SolidColor(theme.accent),
        modifier = modifier
            .fillMaxWidth()
            .background(theme.fieldBg, Radius)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, color = theme.textDim, fontSize = BodySize)
            inner()
        },
    )
}

@Composable
private fun AccentButton(label: String, enabled: Boolean, theme: FjTheme, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(theme.accent, Radius)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = BodySize, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LinkText(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label,
        fontSize = BodySize,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = modifier.clickable(onClick = onClick).padding(6.dp),
    )
}

@Composable
private fun VotePill(
    theme: FjTheme,
    postId: String,
    upvotes: Int,
    hasVoted: Boolean,
    onChange: (Int, Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Keyed on the incoming count/state too, so an authoritative refresh from the caller
    // (e.g. a standalone getVoteState() read) replaces the locally-held value.
    var count by remember(postId, upvotes) { mutableStateOf(upvotes) }
    var voted by remember(postId, hasVoted) { mutableStateOf(hasVoted) }
    var busy by remember(postId) { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(44.dp)
            .then(
                if (voted) Modifier.background(theme.accent, Radius)
                else Modifier.border(1.dp, theme.divider, Radius),
            )
            .clickable(enabled = !busy) {
                val prevVoted = voted
                val prevCount = count
                voted = !prevVoted
                count = if (voted) prevCount + 1 else (prevCount - 1).coerceAtLeast(0)
                busy = true
                scope.launch {
                    val res = if (prevVoted) FeedbackJar.unvote(postId) else FeedbackJar.vote(postId)
                    busy = false
                    res.onSuccess { st ->
                        count = st.upvotes
                        voted = st.hasVoted
                        onChange(st.upvotes, st.hasVoted)
                    }.onFailure {
                        voted = prevVoted
                        count = prevCount
                    }
                }
            }
            .padding(vertical = 6.dp),
    ) {
        Text("▲", fontSize = 10.sp, color = if (voted) Color.White else theme.textDim)
        Text(
            count.toString(),
            fontSize = SmallSize,
            fontWeight = FontWeight.Bold,
            color = if (voted) Color.White else theme.text,
        )
    }
}

// ---- board ----

@Composable
private fun BoardScreen(
    theme: FjTheme,
    config: WidgetConfig,
    boardId: String?,
    reloadKey: Int,
    onOpen: (FeedbackPost) -> Unit,
    onNew: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val posts = remember { mutableStateListOf<FeedbackPost>() }
    var cursor by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load(reset: Boolean) {
        val res = FeedbackJar.listFeedback(boardId = boardId, limit = 20, cursor = if (reset) null else cursor)
        res.onSuccess { page ->
            if (reset) posts.clear()
            posts.addAll(page.posts)
            cursor = page.nextCursor
            error = null
        }.onFailure { error = it.message ?: "Something went wrong." }
    }

    LaunchedEffect(reloadKey) {
        loading = true
        load(reset = true)
        loading = false
    }

    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= posts.size - 3
        }.collect { near ->
            if (near && cursor != null && !loadingMore && !loading) {
                loadingMore = true
                load(reset = false)
                loadingMore = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Feedback", fontSize = BodySize, fontWeight = FontWeight.Bold, color = theme.text, modifier = Modifier.weight(1f))
            LinkText("New", theme.accent) { onNew() }
        }

        when {
            loading -> Spinner(theme, Modifier.fillMaxSize())

            posts.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(error ?: "No feedback yet.", fontSize = SmallSize, color = theme.textDim)
                if (error != null) {
                    LinkText("Retry", theme.accent, Modifier.padding(top = 8.dp)) {
                        scope.launch {
                            loading = true
                            load(reset = true)
                            loading = false
                        }
                    }
                }
            }

            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(posts, key = { it.id }) { post ->
                    Column {
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpen(post) }.padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(post.title, fontSize = BodySize, fontWeight = FontWeight.Bold, color = theme.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(richTextToPlainText(post.content), fontSize = BodySize, color = theme.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                                Text("${humanStatus(post.status)} · ${post.commentCount} comments", fontSize = SmallSize, color = theme.textDim, modifier = Modifier.padding(top = 4.dp))
                            }
                            if (config.allowVotes) {
                                Spacer(Modifier.width(12.dp))
                                VotePill(theme, post.id, post.upvotes, post.hasVoted) { u, v ->
                                    val idx = posts.indexOfFirst { it.id == post.id }
                                    if (idx >= 0) posts[idx] = posts[idx].copy(upvotes = u, hasVoted = v)
                                }
                            }
                        }
                        HairlineDivider(theme)
                    }
                }
                if (loadingMore) {
                    item { Spinner(theme, Modifier.fillMaxWidth().padding(16.dp)) }
                }
            }
        }
    }
}

// ---- detail ----

@Composable
private fun DetailScreen(
    theme: FjTheme,
    config: WidgetConfig,
    post: FeedbackPost,
    commentIdentity: (() -> Pair<String?, String?>?)? = null,
    onBack: () -> Unit,
    onPostPress: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val comments = remember { mutableStateListOf<FeedbackComment>() }
    var cursor by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var replyTo by remember { mutableStateOf<FeedbackComment?>(null) }
    var voteUpvotes by remember(post.id) { mutableStateOf(post.upvotes) }
    var voteHasVoted by remember(post.id) { mutableStateOf(post.hasVoted) }

    // The cached post data can be stale — read the authoritative vote state once on open.
    LaunchedEffect(post.id) {
        if (config.allowVotes) {
            FeedbackJar.getVoteState(post.id).onSuccess { state ->
                voteUpvotes = state.upvotes
                voteHasVoted = state.hasVoted
            }
        }
    }

    suspend fun load(reset: Boolean) {
        val res = FeedbackJar.listComments(post.id, limit = 50, cursor = if (reset) null else cursor)
        res.onSuccess { page ->
            if (reset) comments.clear()
            comments.addAll(page.comments)
            cursor = page.nextCursor
            error = null
        }.onFailure { error = it.message ?: "Something went wrong." }
    }

    LaunchedEffect(post.id) {
        loading = true
        load(reset = true)
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        LinkText("‹ Back", theme.accent, Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) { onBack() }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(post.title, fontSize = BodySize, fontWeight = FontWeight.Bold, color = theme.text, modifier = Modifier.weight(1f))
                if (config.allowVotes) {
                    Spacer(Modifier.width(12.dp))
                    VotePill(theme, post.id, voteUpvotes, voteHasVoted) { u, v ->
                        voteUpvotes = u
                        voteHasVoted = v
                    }
                }
            }
            val meta = buildString {
                append(humanStatus(post.status))
                post.authorName?.let { append(" · ").append(it) }
                append(" · ").append(relativeTime(post.createdAt))
            }
            Text(meta, fontSize = SmallSize, color = theme.textDim, modifier = Modifier.padding(top = 6.dp))
            Box(Modifier.padding(top = 8.dp)) {
                FjRichText(post.content, theme, onPostPress)
            }

            HairlineDivider(theme, Modifier.padding(vertical = 20.dp))

            Text("Comments", fontSize = BodySize, fontWeight = FontWeight.Bold, color = theme.text)
            Spacer(Modifier.height(12.dp))

            when {
                loading -> Spinner(theme, Modifier.fillMaxWidth().padding(12.dp))

                comments.isEmpty() -> Text(
                    error ?: "No comments yet.",
                    fontSize = SmallSize,
                    color = if (error != null) theme.accent else theme.textDim,
                )

                else -> {
                    comments.forEach { root ->
                        CommentRow(theme, root, indented = false, canReply = config.allowComments, onPostPress = onPostPress) { replyTo = root }
                        root.replies.forEach { reply ->
                            CommentRow(theme, reply, indented = true, canReply = false, onPostPress = onPostPress) {}
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                    error?.let { Text(it, fontSize = SmallSize, color = theme.accent) }
                    if (cursor != null) {
                        LinkText("Load more", theme.accent, Modifier.padding(top = 8.dp)) {
                            scope.launch { load(reset = false) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        if (config.allowComments) {
            HairlineDivider(theme)
            replyTo?.let { target ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Replying to ${target.authorName}", fontSize = SmallSize, color = theme.textDim, modifier = Modifier.weight(1f))
                    Text("×", fontSize = BodySize, color = theme.textDim, modifier = Modifier.clickable { replyTo = null }.padding(6.dp))
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Field(draft, { draft = it }, "Add a comment…", theme, Modifier.weight(1f), singleLine = false)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(40.dp)
                        .background(theme.accent, Radius)
                        .clickable(enabled = draft.isNotBlank() && !sending) {
                            val text = draft.trim()
                            val parentId = replyTo?.id
                            val identity = commentIdentity?.invoke()
                            sending = true
                            scope.launch {
                                val res = FeedbackJar.addComment(
                                    post.id,
                                    text,
                                    parentId = parentId,
                                    name = identity?.first,
                                    email = identity?.second,
                                )
                                sending = false
                                res.onSuccess {
                                    draft = ""
                                    replyTo = null
                                    load(reset = true)
                                }.onFailure { error = it.message ?: "Something went wrong." }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("↑", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    theme: FjTheme,
    comment: FeedbackComment,
    indented: Boolean,
    canReply: Boolean,
    onPostPress: (String) -> Unit = {},
    onReply: () -> Unit,
) {
    Column(
        Modifier.padding(
            start = if (indented) 28.dp else 0.dp,
            top = if (indented) 12.dp else 0.dp,
        ),
    ) {
        Row {
            Text(comment.authorName, fontSize = SmallSize, fontWeight = FontWeight.Bold, color = theme.text)
            if (comment.authorRole != null) {
                Text("  TEAM", fontSize = SmallSize, fontWeight = FontWeight.Bold, color = theme.accent)
            }
            Text("  ${relativeTime(comment.createdAt)}", fontSize = SmallSize, color = theme.textDim)
        }
        Box(Modifier.padding(top = 4.dp)) {
            FjRichText(comment.content, theme, onPostPress)
        }
        if (canReply) {
            Text(
                "Reply",
                fontSize = SmallSize,
                fontWeight = FontWeight.Bold,
                color = theme.textDim,
                modifier = Modifier.padding(top = 4.dp).clickable { onReply() },
            )
        }
    }
}

// ---- new feedback ----

@Composable
private fun NewFeedbackScreen(
    theme: FjTheme,
    config: WidgetConfig,
    properties: (() -> Map<String, Any?>?)? = null,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val id = FeedbackJar.getIdentity()
        id.name?.let { name = it }
        id.email?.let { email = it }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinkText("Cancel", theme.textDim) { onCancel() }
            Text(
                "New feedback",
                fontSize = BodySize,
                fontWeight = FontWeight.Bold,
                color = theme.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(52.dp))
        }

        Field(text, { text = it }, "Share your feedback…", theme, Modifier.height(120.dp), singleLine = false)
        if (config.collectName) Field(name, { name = it }, "Name", theme)
        if (config.collectEmail) Field(email, { email = it }, "Email", theme)

        error?.let { Text(it, fontSize = SmallSize, color = theme.accent) }

        AccentButton(
            label = if (sending) "Sending…" else "Send",
            enabled = text.isNotBlank() && !sending,
            theme = theme,
        ) {
            sending = true
            error = null
            scope.launch {
                val res = FeedbackJar.submit(
                    content = text.trim(),
                    email = if (config.collectEmail) email.trim().ifEmpty { null } else null,
                    userName = if (config.collectName) name.trim().ifEmpty { null } else null,
                    properties = properties?.invoke(),
                )
                sending = false
                res.onSuccess {
                    FeedbackJar.setIdentity(
                        name = if (config.collectName) name.trim().ifEmpty { null } else null,
                        email = if (config.collectEmail) email.trim().ifEmpty { null } else null,
                    )
                    onDone()
                }.onFailure { error = it.message ?: "Something went wrong." }
            }
        }
    }
}
