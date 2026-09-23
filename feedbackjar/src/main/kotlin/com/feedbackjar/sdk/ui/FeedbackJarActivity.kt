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

        @JvmStatic
        @JvmOverloads
        fun intent(context: Context, accentColor: Int? = null): Intent =
            Intent(context, FeedbackJarActivity::class.java).apply {
                if (accentColor != null) putExtra(EXTRA_ACCENT_COLOR, accentColor)
            }
    }
}
