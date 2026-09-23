package com.feedbackjar.sdk.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.feedbackjar.sdk.FeedbackJar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Up-chevron glyph + count, stacked. Filled/accent when voted, hairline outline
 * otherwise. Tapping toggles the vote optimistically and rolls back on failure.
 * The chevron is a Unicode glyph — no icon font or vector asset.
 */
@SuppressLint("ViewConstructor")
internal class VotePillView(
    context: Context,
    private val palette: Palette,
    private var accent: Int,
    private val scope: CoroutineScope,
) : LinearLayout(context) {

    private val chevron = TextView(context)
    private val countView = TextView(context)

    private var postId: String = ""
    private var upvotes: Int = 0
    private var voted: Boolean = false
    private var busy: Boolean = false

    /** Called with the authoritative count/state after a successful toggle. */
    var onChange: ((Int, Boolean) -> Unit)? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        minimumWidth = context.dp(44)
        setPadding(context.dp(10), context.dp(6), context.dp(10), context.dp(6))
        isClickable = true
        isFocusable = true

        chevron.text = "▲"
        chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        chevron.includeFontPadding = false
        countView.setTextSize(TypedValue.COMPLEX_UNIT_SP, FONT_SMALL_SP)
        countView.setTypeface(Typeface.DEFAULT_BOLD)
        countView.includeFontPadding = false

        addView(chevron)
        addView(countView)

        setOnClickListener { toggle() }
    }

    fun setAccent(color: Int) {
        accent = color
        render()
    }

    fun bind(postId: String, upvotes: Int, hasVoted: Boolean) {
        if (busy) return
        this.postId = postId
        this.upvotes = upvotes
        this.voted = hasVoted
        render()
    }

    private fun render() {
        val fill = voted
        background = GradientDrawable().apply {
            cornerRadius = context.dp(RADIUS_DP).toFloat()
            setColor(if (fill) accent else Color.TRANSPARENT)
            setStroke(hairline(context), if (fill) accent else palette.divider)
        }
        val fg = if (fill) Color.WHITE else palette.text
        chevron.setTextColor(if (fill) Color.WHITE else palette.textDim)
        countView.text = upvotes.toString()
        countView.setTextColor(fg)
        contentDescription =
            (if (voted) "Remove upvote, " else "Upvote, ") + "$upvotes votes"
    }

    private fun toggle() {
        if (busy) return
        val prevVoted = voted
        val prevCount = upvotes

        voted = !prevVoted
        upvotes = if (voted) prevCount + 1 else (prevCount - 1).coerceAtLeast(0)
        busy = true
        render()

        scope.launch {
            val result = if (prevVoted) FeedbackJar.unvote(postId) else FeedbackJar.vote(postId)
            busy = false
            result
                .onSuccess { state ->
                    upvotes = state.upvotes
                    voted = state.hasVoted
                    render()
                    onChange?.invoke(upvotes, voted)
                }
                .onFailure {
                    voted = prevVoted
                    upvotes = prevCount
                    render()
                }
        }
    }
}
