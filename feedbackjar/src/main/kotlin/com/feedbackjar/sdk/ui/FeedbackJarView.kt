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

    private var scope: CoroutineScope? = null
    private var config = WidgetConfig(
        collectName = false,
        collectEmail = false,
        allowVotes = false,
        allowComments = false,
    )

    private var board: BoardListView? = null

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
            ),
        )
    }

    /** Resolve a `#[title](postId)` mention and open that post's detail screen. */
    private fun openPostById(postId: String) {
        val s = scope ?: return
        s.launch { FeedbackJar.getPost(postId).onSuccess { showDetail(it) } }
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
            ),
        )
    }

    /** True while a screen other than the board is showing — lets a host handle Back. */
    fun onBackPressed(): Boolean {
        if (board?.parent === this) return false
        showBoard()
        return true
    }
}
