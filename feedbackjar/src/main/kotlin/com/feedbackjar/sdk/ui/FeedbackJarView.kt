package com.feedbackjar.sdk.ui

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import com.feedbackjar.sdk.FeedbackJar
import com.feedbackjar.sdk.FeedbackPost
import com.feedbackjar.sdk.WidgetConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * A complete, drop-in feedback board built only on framework views — no
 * RecyclerView, no ConstraintLayout, no Material, no AndroidX UI. List → detail
 * → new feedback, swapped in place.
 *
 * ```kotlin
 * val board = FeedbackJarView(context)
 * board.setAccentColor(Color.parseColor("#e5484d")) // optional
 * setContentView(board)
 * ```
 *
 * Requires [FeedbackJar.init] to have been called. Data calls run on a scope tied
 * to the view's attached state; every failure surfaces inline — nothing throws.
 */
class FeedbackJarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val palette = paletteFor(context)
    private var accent: Int = DEFAULT_ACCENT

    /** Restrict the board to a single FeedbackJar board id. Set before attaching. */
    var boardId: String? = null

    /** Called on each submission from the board's own "New" screen, to get custom
     * key/value pairs merged into the auto-collected metadata. Forwarded verbatim to
     * [FeedbackJar.submit]'s `properties` parameter. */
    var propertiesProvider: (() -> Map<String, Any?>?)? = null

    /** Called on each comment sent from a post's detail screen, to get the name/email
     * (`first`/`second`) to attach to it. Return `null`, or a `null` field, to fall back
     * to the remembered identity. */
    var commentIdentityProvider: (() -> Pair<String?, String?>?)? = null

    private var scope: CoroutineScope? = null
    private var config = WidgetConfig(
        collectName = false,
        collectEmail = false,
        allowVotes = false,
        allowComments = false,
    )

    private var board: BoardListView? = null
    private var pendingOpenPostId: String? = null

    /** Accent for the active vote state, the primary button and links. */
    fun setAccentColor(color: Int) {
        accent = color
        if (isAttachedToWindow) showBoard()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setBackgroundColor(palette.bg)
        val s = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope = s
        showBoard()
        pendingOpenPostId?.let { postId ->
            pendingOpenPostId = null
            openPostById(postId)
        }
        s.launch {
            FeedbackJar.getConfig().onSuccess { loaded ->
                config = loaded
                // Re-render the board so vote pills appear/disappear per config.
                if (board?.parent === this@FeedbackJarView) showBoard()
            }
        }
    }

    override fun onDetachedFromWindow() {
        scope?.cancel()
        scope = null
        super.onDetachedFromWindow()
    }

    private fun swap(view: android.view.View) {
        removeAllViews()
        addView(view, LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    private fun showBoard() {
        val s = scope ?: return
        val view = BoardListView(
            context = context,
            palette = palette,
            accent = accent,
            scope = s,
            configProvider = { config },
            boardId = boardId,
            onOpenPost = { showDetail(it) },
            onNew = { showNew() },
        )
        board = view
        swap(view)
    }

    private fun showDetail(post: FeedbackPost) {
        val s = scope ?: return
        swap(
            FeedbackDetailView(
                context = context,
                palette = palette,
                accent = accent,
                scope = s,
                config = config,
                post = post,
                onBack = { showBoard() },
                onPostPress = { openPostById(it) },
                commentIdentityProvider = commentIdentityProvider,
            ),
        )
    }

    /** Resolve a `#[title](postId)` mention and open that post's detail screen. */
    private fun openPostById(postId: String) {
        val s = scope ?: return
        s.launch { FeedbackJar.getPost(postId).onSuccess { showDetail(it) } }
    }

    /**
     * Jump straight to a post's detail screen — e.g. from a push-notification tap on an
     * already-mounted board. Safe to call before the view is attached to a window; the
     * jump is deferred until then.
     */
    fun openPost(postId: String) {
        if (scope != null) openPostById(postId) else pendingOpenPostId = postId
    }

    private fun showNew() {
        val s = scope ?: return
        swap(
            NewFeedbackView(
                context = context,
                palette = palette,
                accent = accent,
                scope = s,
                config = config,
                onDone = { showBoard() },
                onCancel = { showBoard() },
                propertiesProvider = propertiesProvider,
            ),
        )
    }

    /**
     * Forget the remembered submitter identity (e.g. on logout) and refresh this
     * already-mounted board as a clean anonymous guest: calls [FeedbackJar.clearIdentity],
     * drops back to the board list and reloads it. A subsequent "New feedback" screen
     * picks up the cleared identity automatically.
     */
    fun resetIdentity() {
        FeedbackJar.clearIdentity()
        if (scope != null) showBoard()
    }

    /** True while a screen other than the board is showing — lets a host handle Back. */
    fun onBackPressed(): Boolean {
        if (board?.parent === this) return false
        showBoard()
        return true
    }
}
