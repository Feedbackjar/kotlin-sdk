// ClickableText is deprecated in Compose 1.7 but still present, and it works
// against every Compose version a consumer might bring (the SDK's Compose dep is
// compileOnly). The 1.7-only replacement (withLink / LinkAnnotation) would break
// consumers on older Compose, so we keep this and silence the warning.
@file:Suppress("DEPRECATION")

package com.feedbackjar.sdk.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feedbackjar.sdk.internal.RichBlock
import com.feedbackjar.sdk.internal.RichInline
import com.feedbackjar.sdk.internal.parseRichBlocks

private val RtBody = 15.sp
private val RtSmall = 13.sp

/**
 * Render post / comment content — light Markdown + `#[…]` / `@[…]` mentions.
 * Uses only stable Compose text APIs so it works against the consumer's own
 * (compileOnly) Compose version.
 */
@Composable
internal fun FjRichText(
    md: String,
    theme: FjTheme,
    onPostPress: ((String) -> Unit)? = null,
) {
    val blocks = remember(md) { parseRichBlocks(md) }
    val uriHandler = LocalUriHandler.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            when (block) {
                is RichBlock.Paragraph ->
                    InlineText(annotate(block.children, theme, onPostPress), theme, uriHandler, onPostPress)

                is RichBlock.Heading -> InlineText(
                    annotate(block.children, theme, onPostPress),
                    theme, uriHandler, onPostPress,
                    style = TextStyle(
                        fontSize = if (block.level <= 1) 17.sp else RtBody,
                        fontWeight = FontWeight.Bold,
                        color = theme.text,
                    ),
                )
                is RichBlock.Quote -> Column(
                    Modifier
                        .fillMaxWidth()
                        .background(theme.fieldBg, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    InlineText(
                        annotate(block.children, theme, onPostPress),
                        theme, uriHandler, onPostPress,
                        style = TextStyle(fontSize = RtBody, color = theme.textDim),
                    )
                }

                is RichBlock.BulletList -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEach { item -> ListRow("•", item, theme, uriHandler, onPostPress) }
                }

                is RichBlock.OrderedList -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEachIndexed { i, item ->
                        ListRow("${i + 1}.", item, theme, uriHandler, onPostPress)
                    }
                }

                is RichBlock.CodeBlock -> Text(
                    text = block.value,
                    fontSize = RtSmall,
                    fontFamily = FontFamily.Monospace,
                    color = theme.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.fieldBg, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                )
            }
        }
    }
}

@Composable
private fun ListRow(
    marker: String,
    item: List<RichInline>,
    theme: FjTheme,
    uriHandler: UriHandler,
    onPostPress: ((String) -> Unit)?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(marker, fontSize = RtBody, color = theme.textDim)
        InlineText(annotate(item, theme, onPostPress), theme, uriHandler, onPostPress)
    }
}

@Composable
private fun InlineText(
    text: AnnotatedString,
    theme: FjTheme,
    uriHandler: UriHandler,
    onPostPress: ((String) -> Unit)?,
    style: TextStyle = TextStyle(fontSize = RtBody, color = theme.text),
) {
    ClickableText(
        text = text,
        style = style,
        onClick = { offset ->
            text.getStringAnnotations(start = offset, end = offset).firstOrNull()?.let { ann ->
                when (ann.tag) {
                    "url" -> runCatching { uriHandler.openUri(ann.item) }
                    "post" -> onPostPress?.invoke(ann.item)
                    else -> Unit
                }
            }
        },
    )
}

private fun annotate(
    nodes: List<RichInline>,
    theme: FjTheme,
    onPostPress: ((String) -> Unit)?,
): AnnotatedString = buildAnnotatedString {
    fun walk(list: List<RichInline>, bold: Boolean, italic: Boolean, color: Color?) {
        for (node in list) {
            when (node) {
                is RichInline.Text -> withStyle(
                    SpanStyle(
                        fontWeight = if (bold) FontWeight.Bold else null,
                        fontStyle = if (italic) FontStyle.Italic else null,
                        color = color ?: Color.Unspecified,
                    ),
                ) { append(node.value) }

                is RichInline.Strong -> walk(node.children, true, italic, color)
                is RichInline.Em -> walk(node.children, bold, true, color)

                is RichInline.Code -> withStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, background = theme.fieldBg),
                ) { append(node.value) }

                is RichInline.Link -> {
                    pushStringAnnotation("url", node.href)
                    walk(node.children, bold, italic, theme.accent)
                    pop()
                }

                is RichInline.UserMention -> withStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, color = theme.accent),
                ) { append("@${node.name}") }

                is RichInline.PostMention -> {
                    if (onPostPress != null) pushStringAnnotation("post", node.postId)
                    withStyle(
                        SpanStyle(
                            color = theme.accent,
                            background = theme.accent.copy(alpha = 0.12f),
                            fontWeight = FontWeight.SemiBold,
                        ),
                    ) { append(" #${node.title} ") }
                    if (onPostPress != null) pop()
                }
            }
        }
    }
    walk(nodes, bold = false, italic = false, color = null)
}
