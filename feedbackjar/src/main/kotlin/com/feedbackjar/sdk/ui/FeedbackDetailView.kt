package com.feedbackjar.sdk.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.feedbackjar.sdk.FeedbackComment
import com.feedbackjar.sdk.FeedbackJar
import com.feedbackjar.sdk.FeedbackPost
import com.feedbackjar.sdk.WidgetConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Screen 2 — post detail: title, vote pill, body, two-level comment thread, composer. */
@SuppressLint("ViewConstructor")
internal class FeedbackDetailView(
    context: Context,
    private val palette: Palette,
    private val accent: Int,
    private val scope: CoroutineScope,
    private val config: WidgetConfig,
    private var post: FeedbackPost,
    private val onBack: () -> Unit,
    private val onPostPress: ((String) -> Unit)? = null,
) : LinearLayout(context) {

    private companion object {
        const val PAGE = 50
    }

    private val commentsContainer = LinearLayout(context).apply { orientation = VERTICAL }
    private val comments = mutableListOf<FeedbackComment>()
    private var cursor: String? = null
    private var loadingComments = true
    private var errorMessage: String? = null
    private var sending = false

    private var replyTo: FeedbackComment? = null
    private var composerInput: EditText? = null
    private var replyBanner: View? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(palette.bg)

        val pad = context.dp(20)

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(context.dp(14), context.dp(12), context.dp(14), context.dp(12))
        }
        header.addView(linkText(context, "‹ Back", accent).apply { setOnClickListener { onBack() } })
        addView(header, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val scroll = ScrollView(context).apply { isFillViewport = true }
        val content = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(pad, context.dp(4), pad, pad)
        }

        val topRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.TOP
        }
        topRow.addView(
            titleText(context, palette).apply { text = post.title },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
        )
        if (config.allowVotes) {
            val pill = VotePillView(context, palette, accent, scope)
            pill.bind(post.id, post.upvotes, post.hasVoted)
            pill.onChange = { upvotes, voted -> post = post.copy(upvotes = upvotes, hasVoted = voted) }
            topRow.addView(pill, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                marginStart = context.dp(12)
            })
        }
        content.addView(topRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val meta = buildString {
            append(humanStatus(post.status))
            post.authorName?.let { append(" · ").append(it) }
            append(" · ").append(relativeTime(post.createdAt))
        }
        content.addView(
            smallText(context, palette).apply { text = meta },
            lp(WRAP_CONTENT, WRAP_CONTENT, context.dp(6)),
        )
        content.addView(
            bodyText(context, palette).apply {
                setRichText(post.content, palette, accent, onPostPress)
            },
            lp(MATCH_PARENT, WRAP_CONTENT, context.dp(8)),
        )

        content.addView(dividerView(context, palette), lp(MATCH_PARENT, WRAP_CONTENT, context.dp(20)))

        content.addView(
            titleText(context, palette).apply { text = "Comments" },
            lp(WRAP_CONTENT, WRAP_CONTENT, context.dp(2)),
        )
        content.addView(
            commentsContainer,
            lp(MATCH_PARENT, WRAP_CONTENT, context.dp(12)),
        )

        scroll.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        if (config.allowComments) addView(buildComposer(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        renderComments()
        loadComments(reset = true)
    }

    private fun loadComments(reset: Boolean) {
        if (reset) {
            loadingComments = true
            renderComments()
        }
        scope.launch {
            val res = FeedbackJar.listComments(
                post.id,
                limit = PAGE,
                cursor = if (reset) null else cursor,
            )
            loadingComments = false
            res.onSuccess { page ->
                if (reset) comments.clear()
                comments.addAll(page.comments)
                cursor = page.nextCursor
                errorMessage = null
            }.onFailure { errorMessage = it.message ?: "Something went wrong." }
            renderComments()
        }
    }

    private fun renderComments() {
        commentsContainer.removeAllViews()

        if (loadingComments) {
            commentsContainer.addView(
                spinner(context, accent),
                lp(WRAP_CONTENT, WRAP_CONTENT, context.dp(12)).apply { gravity = Gravity.START },
            )
            return
        }

        if (comments.isEmpty()) {
            commentsContainer.addView(smallText(context, palette).apply {
                text = errorMessage ?: "No comments yet."
                if (errorMessage != null) setTextColor(accent)
            })
            return
        }

        for (root in comments) {
            commentsContainer.addView(commentView(root, indented = false), spacing(18))
            for (reply in root.replies) {
                commentsContainer.addView(commentView(reply, indented = true), spacing(12))
            }
        }

        errorMessage?.let { msg ->
            commentsContainer.addView(
                smallText(context, palette).apply {
                    text = msg
                    setTextColor(accent)
                },
                spacing(12),
            )
        }

        if (cursor != null) {
            commentsContainer.addView(
                linkText(context, "Load more", accent).apply {
                    setOnClickListener { loadComments(reset = false) }
                },
                spacing(8),
            )
        }
    }

    private fun spacing(topDp: Int) =
        lp(MATCH_PARENT, WRAP_CONTENT, context.dp(topDp))

    private fun commentView(comment: FeedbackComment, indented: Boolean): View {
        val column = LinearLayout(context).apply { orientation = VERTICAL }

        val head = SpannableStringBuilder()
        head.append(comment.authorName)
        head.setSpan(StyleSpan(Typeface.BOLD), 0, head.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        head.setSpan(
            ForegroundColorSpan(palette.text),
            0,
            head.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        if (comment.authorRole != null) {
            val start = head.length
            head.append("  TEAM")
            head.setSpan(StyleSpan(Typeface.BOLD), start, head.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            head.setSpan(ForegroundColorSpan(accent), start, head.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val dateStart = head.length
        head.append("  ").append(relativeTime(comment.createdAt))
        head.setSpan(
            ForegroundColorSpan(palette.textDim),
            dateStart,
            head.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )

        column.addView(TextView(context).apply {
            text = head
            setTextSize(TypedValue.COMPLEX_UNIT_SP, FONT_SMALL_SP)
        })
        column.addView(
            bodyText(context, palette).apply {
                setRichText(comment.content, palette, accent, onPostPress)
            },
            lp(MATCH_PARENT, WRAP_CONTENT, context.dp(4)),
        )

        if (!indented && config.allowComments) {
            column.addView(
                linkText(context, "Reply", palette.textDim).apply {
                    setPadding(0, context.dp(4), 0, 0)
                    setOnClickListener { setReplyTarget(comment) }
                },
                lp(WRAP_CONTENT, WRAP_CONTENT, context.dp(2)),
            )
        }

        if (!indented) return column

        // Reply: indent one step with a hairline left rule.
        val indent = LinearLayout(context).apply { orientation = HORIZONTAL }
        indent.addView(View(context).apply { setBackgroundColor(palette.divider) },
            LinearLayout.LayoutParams(hairline(context), MATCH_PARENT))
        indent.addView(column, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            marginStart = context.dp(12)
        })
        val outer = LinearLayout(context).apply { orientation = VERTICAL }
        outer.addView(indent, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            marginStart = context.dp(16)
        })
        return outer
    }

    // ---- composer ----

    private fun buildComposer(): View {
        val bar = LinearLayout(context).apply {
            orientation = VERTICAL
            setBackgroundColor(palette.bg)
        }
        bar.addView(dividerView(context, palette))

        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.BOTTOM
            val p = context.dp(12)
            setPadding(p, p, p, p)
        }

        val input = field(context, palette, "Add a comment…", multiline = true).apply {
            minHeight = context.dp(40)
            maxHeight = context.dp(120)
        }
        composerInput = input
        row.addView(input, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        val send = TextView(context).apply {
            text = "↑"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(Typeface.DEFAULT_BOLD)
            background = context.roundedRect(accent)
            isClickable = true
            isFocusable = true
            contentDescription = "Send comment"
            setOnClickListener { sendComment() }
        }
        row.addView(send, LinearLayout.LayoutParams(context.dp(40), context.dp(40)).apply {
            marginStart = context.dp(8)
        })

        bar.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return bar
    }

    private fun setReplyTarget(comment: FeedbackComment) {
        replyTo = comment
        replyBanner?.let { (it.parent as? LinearLayout)?.removeView(it) }
        val composer = composerInput?.parent?.parent as? LinearLayout ?: return

        val banner = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = context.dp(12)
            setPadding(p, context.dp(8), p, 0)
        }
        banner.addView(
            smallText(context, palette).apply { text = "Replying to ${comment.authorName}" },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
        )
        banner.addView(linkText(context, "×", palette.textDim).apply {
            setOnClickListener { clearReplyTarget() }
        })
        replyBanner = banner
        composer.addView(banner, 1)
        composerInput?.requestFocus()
    }

    private fun clearReplyTarget() {
        replyTo = null
        replyBanner?.let { (it.parent as? LinearLayout)?.removeView(it) }
        replyBanner = null
    }

    private fun sendComment() {
        val input = composerInput ?: return
        val text = input.text.toString().trim()
        if (text.isEmpty() || sending) return
        sending = true
        input.setEnabledAlpha(false)

        val parentId = replyTo?.id
        scope.launch {
            val res = FeedbackJar.addComment(post.id, text, parentId = parentId)
            sending = false
            input.setEnabledAlpha(true)
            res.onSuccess {
                input.setText("")
                clearReplyTarget()
                loadComments(reset = true)
            }.onFailure {
                errorMessage = it.message ?: "Something went wrong."
                renderComments()
            }
        }
    }
}
