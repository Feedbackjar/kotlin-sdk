package com.feedbackjar.sdk.ui

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.feedbackjar.sdk.FeedbackJar
import com.feedbackjar.sdk.WidgetConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Screen 3 — new feedback form. Name/email fields per config, prefilled from identity. */
@SuppressLint("ViewConstructor")
internal class NewFeedbackView(
    context: Context,
    private val palette: Palette,
    private val accent: Int,
    private val scope: CoroutineScope,
    private val config: WidgetConfig,
    private val onDone: () -> Unit,
    private val onCancel: () -> Unit,
    /** Called on submit, to get custom key/value pairs merged into the auto-collected
     * metadata. Forwarded verbatim to [FeedbackJar.submit]'s `properties` parameter. */
    private val propertiesProvider: (() -> Map<String, Any?>?)? = null,
) : LinearLayout(context) {

    private var sending = false
    private val errorText: TextView
    private val sendButton: TextView

    init {
        orientation = VERTICAL
        setBackgroundColor(palette.bg)

        val scroll = ScrollView(context).apply { isFillViewport = true }
        val pad = context.dp(20)
        val content = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(linkText(context, "Cancel", palette.textDim).apply {
            setOnClickListener { onCancel() }
        })
        header.addView(
            titleText(context, palette).apply {
                text = "New feedback"
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
        )
        header.addView(TextView(context), LinearLayout.LayoutParams(context.dp(52), WRAP_CONTENT))
        content.addView(header, lp(MATCH_PARENT, WRAP_CONTENT, context.dp(0)))

        val message = field(context, palette, "Share your feedback…", multiline = true)
        content.addView(message, lp(MATCH_PARENT, WRAP_CONTENT, context.dp(12)))

        val identity = FeedbackJar.getIdentity()

        val nameField = if (config.collectName) {
            field(context, palette, "Name", multiline = false).also {
                it.setText(identity.name ?: "")
                content.addView(it, lp(MATCH_PARENT, WRAP_CONTENT, context.dp(12)))
            }
        } else null

        val emailField = if (config.collectEmail) {
            field(context, palette, "Email", multiline = false).also {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                it.setText(identity.email ?: "")
                content.addView(it, lp(MATCH_PARENT, WRAP_CONTENT, context.dp(12)))
            }
        } else null

        errorText = smallText(context, palette).apply {
            setTextColor(accent)
            visibility = GONE
        }
        content.addView(errorText, lp(MATCH_PARENT, WRAP_CONTENT, context.dp(12)))

        sendButton = primaryButton(context, accent, "Send").apply {
            setOnClickListener {
                submit(
                    message.text.toString().trim(),
                    nameField?.text?.toString()?.trim(),
                    emailField?.text?.toString()?.trim(),
                )
            }
        }
        content.addView(sendButton, lp(MATCH_PARENT, WRAP_CONTENT, context.dp(4)))

        scroll.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    private fun submit(text: String, name: String?, email: String?) {
        if (text.isEmpty() || sending) return
        sending = true
        sendButton.setEnabledAlpha(false)
        sendButton.text = "Sending…"
        errorText.visibility = GONE

        scope.launch {
            val res = FeedbackJar.submit(
                content = text,
                email = if (config.collectEmail) email?.takeIf { it.isNotEmpty() } else null,
                userName = if (config.collectName) name?.takeIf { it.isNotEmpty() } else null,
                properties = propertiesProvider?.invoke(),
            )
            sending = false
            sendButton.setEnabledAlpha(true)
            sendButton.text = "Send"
            res.onSuccess {
                // submit() already persists identity; keep it explicit for name-only / email-only edits.
                FeedbackJar.setIdentity(
                    name = if (config.collectName) name?.takeIf { it.isNotEmpty() } else null,
                    email = if (config.collectEmail) email?.takeIf { it.isNotEmpty() } else null,
                )
                android.widget.Toast
                    .makeText(context, "Thanks for your feedback!", android.widget.Toast.LENGTH_SHORT)
                    .show()
                onDone()
            }.onFailure {
                errorText.text = it.message ?: "Something went wrong."
                errorText.visibility = VISIBLE
            }
        }
    }
}
