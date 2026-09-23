package com.feedbackjar.sdk.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.View
import android.widget.TextView
import com.feedbackjar.sdk.internal.RichBlock
import com.feedbackjar.sdk.internal.RichInline
import com.feedbackjar.sdk.internal.parseRichBlocks

/**
 * Render post / comment content — light Markdown + `#[…]` / `@[…]` mentions —
 * into this [TextView]. Framework text spans only; no extra dependency.
 */
internal fun TextView.setRichText(
    md: String,
    palette: Palette,
    accent: Int,
    onPostClick: ((String) -> Unit)?,
) {
    text = buildRichText(md, palette, accent, onPostClick)
    movementMethod = LinkMovementMethod.getInstance()
}

private const val FLAG = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE

private fun tint(color: Int): Int =
    Color.argb(31, Color.red(color), Color.green(color), Color.blue(color))

private fun buildRichText(
    md: String,
    palette: Palette,
    accent: Int,
    onPostClick: ((String) -> Unit)?,
): CharSequence {
    val sb = SpannableStringBuilder()
    parseRichBlocks(md).forEachIndexed { index, block ->
        if (index > 0) sb.append("\n\n")
        when (block) {
            is RichBlock.Paragraph ->
                appendInline(sb, block.children, palette, accent, onPostClick)

            is RichBlock.Heading -> {
                val start = sb.length
                appendInline(sb, block.children, palette, accent, onPostClick)
                sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, FLAG)
                if (block.level <= 1) {
                    sb.setSpan(RelativeSizeSpan(1.15f), start, sb.length, FLAG)
                }
            }

            is RichBlock.Quote -> {
                val start = sb.length
                appendInline(sb, block.children, palette, accent, onPostClick)
                // QuoteSpan(color) needs API 28; the plain ctor works from API 1.
                sb.setSpan(QuoteSpan(), start, sb.length, FLAG)
                sb.setSpan(ForegroundColorSpan(palette.textDim), start, sb.length, FLAG)
            }

            is RichBlock.BulletList -> block.items.forEachIndexed { i, item ->
                if (i > 0) sb.append("\n")
                sb.append("•  ")
                appendInline(sb, item, palette, accent, onPostClick)
            }

            is RichBlock.OrderedList -> block.items.forEachIndexed { i, item ->
                if (i > 0) sb.append("\n")
                sb.append("${i + 1}.  ")
                appendInline(sb, item, palette, accent, onPostClick)
            }

            is RichBlock.CodeBlock -> {
                val start = sb.length
                sb.append(block.value)
                sb.setSpan(TypefaceSpan("monospace"), start, sb.length, FLAG)
                sb.setSpan(BackgroundColorSpan(palette.fieldBg), start, sb.length, FLAG)
            }
        }
    }
    return sb
}

private fun appendInline(
    sb: SpannableStringBuilder,
    nodes: List<RichInline>,
    palette: Palette,
    accent: Int,
    onPostClick: ((String) -> Unit)?,
    bold: Boolean = false,
    italic: Boolean = false,
    color: Int? = null,
    href: String? = null,
) {
    for (node in nodes) {
        when (node) {
            is RichInline.Text -> {
                val start = sb.length
                sb.append(node.value)
                if (bold && italic) {
                    sb.setSpan(StyleSpan(Typeface.BOLD_ITALIC), start, sb.length, FLAG)
                } else if (bold) {
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, FLAG)
                } else if (italic) {
                    sb.setSpan(StyleSpan(Typeface.ITALIC), start, sb.length, FLAG)
                }
                if (color != null) {
                    sb.setSpan(ForegroundColorSpan(color), start, sb.length, FLAG)
                }
                if (href != null) {
                    val url = href
                    sb.setSpan(object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            runCatching {
                                widget.context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                )
                            }
                        }
                    }, start, sb.length, FLAG)
                }
            }

            is RichInline.Strong ->
                appendInline(sb, node.children, palette, accent, onPostClick, bold = true, italic = italic, color = color, href = href)

            is RichInline.Em ->
                appendInline(sb, node.children, palette, accent, onPostClick, bold = bold, italic = true, color = color, href = href)

            is RichInline.Code -> {
                val start = sb.length
                sb.append(node.value)
                sb.setSpan(TypefaceSpan("monospace"), start, sb.length, FLAG)
                sb.setSpan(BackgroundColorSpan(palette.fieldBg), start, sb.length, FLAG)
            }

            is RichInline.Link ->
                appendInline(sb, node.children, palette, accent, onPostClick, bold = bold, italic = italic, color = accent, href = node.href)

            is RichInline.UserMention -> {
                val start = sb.length
                sb.append("@").append(node.name)
                sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, FLAG)
                sb.setSpan(ForegroundColorSpan(accent), start, sb.length, FLAG)
            }

            is RichInline.PostMention -> {
                val start = sb.length
                sb.append(" #").append(node.title).append(" ")
                sb.setSpan(ForegroundColorSpan(accent), start, sb.length, FLAG)
                sb.setSpan(BackgroundColorSpan(tint(accent)), start, sb.length, FLAG)
                if (onPostClick != null) {
                    val id = node.postId
                    sb.setSpan(object : ClickableSpan() {
                        override fun onClick(widget: View) = onPostClick(id)
                    }, start, sb.length, FLAG)
                }
            }
        }
    }
}
