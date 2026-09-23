package com.feedbackjar.sdk.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Shared design tokens for the prebuilt Views UI. Deliberately tiny — two text
 * sizes, three greys, one accent, one corner radius. See UI-SPEC.md.
 */

/** FeedbackJar red — the default accent, caller-overridable. */
internal const val DEFAULT_ACCENT: Int = 0xFFE5484D.toInt()

internal const val FONT_BODY_SP = 15f
internal const val FONT_SMALL_SP = 13f
internal const val RADIUS_DP = 8f

/** The three greys plus the surface background, resolved for the current mode. */
internal data class Palette(
    val bg: Int,
    val text: Int,
    val textDim: Int,
    val divider: Int,
    val fieldBg: Int,
)

private val LIGHT = Palette(
    bg = Color.parseColor("#FFFFFF"),
    text = Color.parseColor("#1A1A1A"),
    textDim = Color.parseColor("#767676"),
    divider = Color.parseColor("#E6E6E6"),
    fieldBg = Color.parseColor("#F4F4F4"),
)

private val DARK = Palette(
    bg = Color.parseColor("#151515"),
    text = Color.parseColor("#F2F2F2"),
    textDim = Color.parseColor("#9A9A9A"),
    divider = Color.parseColor("#2C2C2C"),
    fieldBg = Color.parseColor("#242424"),
)

internal fun Context.isNightMode(): Boolean =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

internal fun paletteFor(context: Context): Palette =
    if (context.isNightMode()) DARK else LIGHT

internal fun Context.dp(value: Float): Int =
    (value * resources.displayMetrics.density + 0.5f).toInt()

internal fun Context.dp(value: Int): Int = dp(value.toFloat())

/** 1px hairline at any density. */
internal fun hairline(context: Context): Int =
    maxOf(1, context.dp(0.5f))

/** Compact relative time: "just now", "3m", "2h", "5d", "3w". */
internal fun relativeTime(iso: String): String {
    val then = parseIso(iso) ?: return ""
    val s = ((System.currentTimeMillis() - then) / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60}m"
        s < 86400 -> "${s / 3600}h"
        s < 604800 -> "${s / 86400}d"
        else -> "${s / 604800}w"
    }
}

private fun parseIso(iso: String): Long? = runCatching {
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    fmt.parse(iso.take(19))?.time
}.getOrNull()

/** "OPEN" -> "Open", "IN_PROGRESS" -> "In progress". */
internal fun humanStatus(status: String): String {
    val s = status.replace('_', ' ').lowercase(Locale.US)
    return s.replaceFirstChar { it.uppercase(Locale.US) }
}
