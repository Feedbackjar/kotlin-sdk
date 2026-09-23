package com.feedbackjar.sdk.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** Small view factories so the screens stay declarative. Framework classes only. */

internal fun Context.roundedRect(fill: Int, stroke: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        cornerRadius = dp(RADIUS_DP).toFloat()
        setColor(fill)
        if (stroke != null) setStroke(hairline(this@roundedRect), stroke)
    }

internal fun bodyText(context: Context, palette: Palette): TextView =
    TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, FONT_BODY_SP)
        setTextColor(palette.text)
    }

internal fun smallText(context: Context, palette: Palette): TextView =
    TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, FONT_SMALL_SP)
        setTextColor(palette.textDim)
    }

internal fun titleText(context: Context, palette: Palette): TextView =
    bodyText(context, palette).apply { setTypeface(Typeface.DEFAULT_BOLD) }

internal fun linkText(context: Context, label: String, color: Int): TextView =
    TextView(context).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, FONT_BODY_SP)
        setTypeface(Typeface.DEFAULT_BOLD)
        setTextColor(color)
        isClickable = true
        isFocusable = true
        val pad = context.dp(6)
        setPadding(pad, pad, pad, pad)
    }

internal fun field(context: Context, palette: Palette, hint: String, multiline: Boolean): EditText =
    EditText(context).apply {
        this.hint = hint
        setHintTextColor(palette.textDim)
        setTextColor(palette.text)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, FONT_BODY_SP)
        background = context.roundedRect(palette.fieldBg)
        val h = context.dp(12)
        val v = context.dp(10)
        setPadding(h, v, h, v)
        if (multiline) {
            setSingleLine(false)
            gravity = Gravity.TOP or Gravity.START
            minHeight = context.dp(120)
        } else {
            setSingleLine(true)
        }
    }

internal fun primaryButton(context: Context, accent: Int, label: String): TextView =
    TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, FONT_BODY_SP)
        setTypeface(Typeface.DEFAULT_BOLD)
        background = context.roundedRect(accent)
        val v = context.dp(13)
        setPadding(0, v, 0, v)
        isClickable = true
        isFocusable = true
    }

internal fun dividerView(context: Context, palette: Palette): View =
    View(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            hairline(context),
        )
        setBackgroundColor(palette.divider)
    }

internal fun spinner(context: Context, accent: Int): ProgressBar =
    ProgressBar(context).apply {
        isIndeterminate = true
        indeterminateDrawable?.setTint(accent)
    }

internal fun TextView.ellipsize1() {
    setSingleLine(true)
    ellipsize = TextUtils.TruncateAt.END
}

internal fun TextView.clampLines(max: Int) {
    setSingleLine(false)
    maxLines = max
    ellipsize = TextUtils.TruncateAt.END
}

internal fun View.setEnabledAlpha(enabled: Boolean) {
    isEnabled = enabled
    alpha = if (enabled) 1f else 0.5f
}

internal fun lp(width: Int, height: Int, topMargin: Int = 0): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(width, height).apply { this.topMargin = topMargin }
