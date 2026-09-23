package com.feedbackjar.sdk.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.feedbackjar.sdk.FeedbackJar
import com.feedbackjar.sdk.FeedbackPost
import com.feedbackjar.sdk.WidgetConfig
import com.feedbackjar.sdk.internal.richTextToPlainText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Screen 1 — the board. List with header, optimistic vote pills, infinite scroll. */
@SuppressLint("ViewConstructor")
internal class BoardListView(
    context: Context,
    private val palette: Palette,
    private val accent: Int,
    private val scope: CoroutineScope,
    private val configProvider: () -> WidgetConfig,
    private val boardId: String?,
    private val onOpenPost: (FeedbackPost) -> Unit,
    private val onNew: () -> Unit,
) : LinearLayout(context) {

    private companion object {
        const val PAGE = 20
    }

    private val listContainer = LinearLayout(context).apply { orientation = VERTICAL }

    private val posts = mutableListOf<FeedbackPost>()
    private var cursor: String? = null
    private var loading = true
    private var loadingMore = false
    private var errorMessage: String? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(palette.bg)

        val pad = context.dp(20)

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, context.dp(16), pad, context.dp(16))
        }
        header.addView(
            titleText(context, palette).apply { text = "Feedback" },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
        )
        header.addView(linkText(context, "New", accent).apply { setOnClickListener { onNew() } })
        addView(header, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val scroll = object : ScrollView(context) {
            override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
                super.onScrollChanged(l, t, oldl, oldt)
                maybeLoadMore()
            }
        }.apply { isFillViewport = true }

        val inner = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(pad, 0, pad, context.dp(24))
        }
        inner.addView(listContainer, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        scroll.addView(inner, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        reload()
    }

    fun reload() {
        posts.clear()
        cursor = null
        loading = true
        loadingMore = false
        errorMessage = null
        render()
        scope.launch {
            val res = FeedbackJar.listFeedback(boardId = boardId, limit = PAGE)
            loading = false
            res.onSuccess { page ->
                posts.addAll(page.posts)
                cursor = page.nextCursor
                errorMessage = null
            }.onFailure { errorMessage = it.message ?: "Something went wrong." }
            render()
        }
    }

    private fun maybeLoadMore() {
        if (loading || loadingMore || cursor == null) return
        val scroll = (listContainer.parent as? View)?.parent as? ScrollView ?: return
        val child = scroll.getChildAt(0) ?: return
        val remaining = child.bottom - (scroll.height + scroll.scrollY)
        if (remaining < context.dp(320)) loadMore()
    }

    private fun loadMore() {
        val c = cursor ?: return
        loadingMore = true
        render()
        scope.launch {
            val res = FeedbackJar.listFeedback(boardId = boardId, limit = PAGE, cursor = c)
            loadingMore = false
            res.onSuccess { page ->
                posts.addAll(page.posts)
                cursor = page.nextCursor
            }.onFailure { errorMessage = it.message ?: "Something went wrong." }
            render()
        }
    }

    private fun render() {
        listContainer.removeAllViews()
        when {
            loading -> {
                listContainer.gravity = Gravity.CENTER_HORIZONTAL
                listContainer.addView(
                    spinner(context, accent),
                    lp(WRAP_CONTENT, WRAP_CONTENT, context.dp(40)),
                )
            }

            posts.isEmpty() -> {
                listContainer.gravity = Gravity.CENTER_HORIZONTAL
                listContainer.addView(
                    smallText(context, palette).apply {
                        text = errorMessage ?: "No feedback yet."
                        gravity = Gravity.CENTER
                    },
                    lp(WRAP_CONTENT, WRAP_CONTENT, context.dp(40)),
                )
                if (errorMessage != null) {
                    listContainer.addView(
                        linkText(context, "Retry", accent).apply { setOnClickListener { reload() } },
                        lp(WRAP_CONTENT, WRAP_CONTENT, context.dp(8)),
                    )
                }
            }

            else -> {
                listContainer.gravity = Gravity.START or Gravity.TOP
                for (post in posts) {
                    listContainer.addView(
                        rowView(post),
                        LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT),
                    )
                }
                if (loadingMore) {
                    listContainer.addView(
                        spinner(context, accent),
                        lp(WRAP_CONTENT, WRAP_CONTENT, context.dp(16)).apply {
                            gravity = Gravity.CENTER_HORIZONTAL
                        },
                    )
                }
                errorMessage?.let { msg ->
                    listContainer.addView(
                        smallText(context, palette).apply {
                            text = msg
                            setTextColor(accent)
                        },
                        lp(WRAP_CONTENT, WRAP_CONTENT, context.dp(12)),
                    )
                }
            }
        }
    }

    private fun rowView(post: FeedbackPost): View {
        val wrap = LinearLayout(context).apply { orientation = VERTICAL }

        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val v = context.dp(16)
            setPadding(0, v, 0, v)
            isClickable = true
            isFocusable = true
            setOnClickListener { onOpenPost(post) }
        }

        val main = LinearLayout(context).apply { orientation = VERTICAL }
        main.addView(titleText(context, palette).apply {
            text = post.title
            ellipsize1()
        })
        main.addView(bodyText(context, palette).apply {
            text = richTextToPlainText(post.content)
            setTextColor(palette.textDim)
            clampLines(2)
        }, lp(MATCH_PARENT, WRAP_CONTENT, context.dp(4)))
        main.addView(smallText(context, palette).apply {
            text = "${humanStatus(post.status)} · ${post.commentCount} comments"
        }, lp(WRAP_CONTENT, WRAP_CONTENT, context.dp(4)))

        row.addView(main, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        if (configProvider().allowVotes) {
            val pill = VotePillView(context, palette, accent, scope)
            pill.bind(post.id, post.upvotes, post.hasVoted)
            pill.onChange = { upvotes, voted ->
                val idx = posts.indexOfFirst { it.id == post.id }
                if (idx >= 0) posts[idx] = posts[idx].copy(upvotes = upvotes, hasVoted = voted)
            }
            row.addView(pill, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                marginStart = context.dp(12)
            })
        }

        wrap.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        wrap.addView(dividerView(context, palette))
        return wrap
    }
}
