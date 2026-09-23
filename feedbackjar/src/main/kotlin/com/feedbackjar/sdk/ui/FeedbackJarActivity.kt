package com.feedbackjar.sdk.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.feedbackjar.sdk.FeedbackJar

/**
 * A full-screen host for [FeedbackJarView]. Register-free to launch:
 *
 * ```kotlin
 * startActivity(FeedbackJarActivity.intent(context))
 * // optional accent:
 * startActivity(FeedbackJarActivity.intent(context, Color.parseColor("#e5484d")))
 * // jump straight into a post's detail screen, e.g. from a push notification:
 * startActivity(FeedbackJarActivity.intent(context, postId = "post_123"))
 * ```
 *
 * Requires [FeedbackJar.init] to have been called first.
 */
class FeedbackJarActivity : Activity() {

    private lateinit var board: FeedbackJarView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        board = FeedbackJarView(this)
        if (intent.hasExtra(EXTRA_ACCENT_COLOR)) {
            board.setAccentColor(intent.getIntExtra(EXTRA_ACCENT_COLOR, DEFAULT_ACCENT))
        }
        window.setBackgroundDrawable(null)
        setContentView(board)
        // A fresh FeedbackJarView reads FeedbackJar.getIdentity() live when it builds its
        // "New feedback" screen, so a cleared identity is always picked up on this launch.
        intent.getStringExtra(EXTRA_POST_ID)?.let { board.openPost(it) }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!board.onBackPressed()) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    companion object {
        /** Optional `int` (packed ARGB) colour extra for the board accent. */
        const val EXTRA_ACCENT_COLOR = "com.feedbackjar.sdk.ui.ACCENT_COLOR"

        /** Optional `String` post id extra — launches straight into that post's detail
         * screen, e.g. from a push-notification tap. */
        const val EXTRA_POST_ID = "com.feedbackjar.sdk.ui.POST_ID"

        @JvmStatic
        @JvmOverloads
        fun intent(context: Context, accentColor: Int? = null, postId: String? = null): Intent =
            Intent(context, FeedbackJarActivity::class.java).apply {
                if (accentColor != null) putExtra(EXTRA_ACCENT_COLOR, accentColor)
                if (postId != null) putExtra(EXTRA_POST_ID, postId)
            }
    }
}
