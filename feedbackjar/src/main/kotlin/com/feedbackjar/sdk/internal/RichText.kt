package com.feedbackjar.sdk.internal

/**
 * Dependency-free parser for the subset of Markdown feedback posts and comments
 * use, plus FeedbackJar mention tokens:
 *
 * - `#[Post title](postId)`                     -> post reference chip
 * - `@[Name](user:id | guest:id | post:id)`     -> user mention
 * - **bold**, *italic*, `code`, [links](url), bare URLs
 * - headings, `-`/`1.` lists, `>` quotes, ``` fenced code
 *
 * Single newlines are hard breaks (matches the web renderer's `remark-breaks`).
 * The Views and Compose UIs each render this model; see `ui/RichTextView` and
 * `ui/compose/RichTextCompose`.
 */

internal sealed interface RichInline {
    data class Text(val value: String) : RichInline
    data class Strong(val children: List<RichInline>) : RichInline
    data class Em(val children: List<RichInline>) : RichInline
    data class Code(val value: String) : RichInline
    data class Link(val href: String, val children: List<RichInline>) : RichInline
    data class PostMention(val title: String, val postId: String) : RichInline
    data class UserMention(val name: String) : RichInline
}

internal sealed interface RichBlock {
    data class Paragraph(val children: List<RichInline>) : RichBlock
    data class Heading(val level: Int, val children: List<RichInline>) : RichBlock
    data class BulletList(val items: List<List<RichInline>>) : RichBlock
    data class OrderedList(val items: List<List<RichInline>>) : RichBlock
    data class Quote(val children: List<RichInline>) : RichBlock
    data class CodeBlock(val value: String) : RichBlock
}

private val DOTALL = setOf(RegexOption.DOT_MATCHES_ALL)

private val POST_MENTION = Regex("""^#\[([^\]]+)\]\(([^)]+)\)""", DOTALL)
private val USER_MENTION = Regex("""^@\[([^\]]+)\]\((?:user|guest|post):([^)]+)\)""", DOTALL)
private val CODE_SPAN = Regex("""^(`+)(.+?)\1""", DOTALL)
private val LINK = Regex("""^\[([^\]]+)\]\(([^)\s]+)(?:\s+"[^"]*")?\)""", DOTALL)
private val STRONG = Regex("""^(\*\*|__)(.+?)\1""", DOTALL)
private val EM = Regex("""^([*_])(\S(?:.*?\S)?|\S)\1""", DOTALL)
private val AUTOLINK = Regex("""^(https?://[^\s<]+[^\s<.,:;"'!?)\]])""")

private val SPECIAL = charArrayOf('`', '#', '@', '[', '*', '_')

private fun parseInline(src: String): List<RichInline> {
    val out = mutableListOf<RichInline>()
    var rest = src
    val buf = StringBuilder()
    fun flush() {
        if (buf.isNotEmpty()) {
            out.add(RichInline.Text(buf.toString()))
            buf.setLength(0)
        }
    }

    while (rest.isNotEmpty()) {
        val code = CODE_SPAN.matchAt(rest, 0)
        if (code != null) {
            flush()
            out.add(RichInline.Code(code.groupValues[2].trim()))
            rest = rest.substring(code.value.length)
            continue
        }
        val postM = POST_MENTION.matchAt(rest, 0)
        if (postM != null) {
            flush()
            out.add(RichInline.PostMention(postM.groupValues[1].trim(), postM.groupValues[2].trim()))
            rest = rest.substring(postM.value.length)
            continue
        }
        val userM = USER_MENTION.matchAt(rest, 0)
        if (userM != null) {
            flush()
            out.add(RichInline.UserMention(userM.groupValues[1].trim()))
            rest = rest.substring(userM.value.length)
            continue
        }
        val link = LINK.matchAt(rest, 0)
        if (link != null) {
            flush()
            out.add(RichInline.Link(link.groupValues[2], parseInline(link.groupValues[1])))
            rest = rest.substring(link.value.length)
            continue
        }
        val strong = STRONG.matchAt(rest, 0)
        if (strong != null) {
            flush()
            out.add(RichInline.Strong(parseInline(strong.groupValues[2])))
            rest = rest.substring(strong.value.length)
            continue
        }
        val em = EM.matchAt(rest, 0)
        if (em != null) {
            flush()
            out.add(RichInline.Em(parseInline(em.groupValues[2])))
            rest = rest.substring(em.value.length)
            continue
        }
        val auto = AUTOLINK.matchAt(rest, 0)
        if (auto != null) {
            flush()
            out.add(RichInline.Link(auto.groupValues[1], listOf(RichInline.Text(auto.groupValues[1]))))
            rest = rest.substring(auto.value.length)
            continue
        }

        // Plain text — always consume char 0 (guarantees progress), then take up
        // to the next possibly-special character.
        buf.append(rest[0])
        rest = rest.substring(1)
        var cut = rest.length
        for (j in rest.indices) {
            val c = rest[j]
            if (c in SPECIAL || (c == 'h' && rest.startsWith("http", j))) {
                cut = j
                break
            }
        }
        buf.append(rest.substring(0, cut))
        rest = rest.substring(cut)
    }
    flush()
    return out
}

private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
private val FENCE = Regex("""^(```|~~~)""")
private val QUOTE = Regex("""^>\s?""")
private val BULLET = Regex("""^\s*[-*+]\s+""")
private val ORDERED = Regex("""^\s*\d+[.)]\s+""")

private fun stripPrefix(regex: Regex, s: String): String {
    val m = regex.matchAt(s, 0) ?: return s
    return s.substring(m.value.length)
}

internal fun parseRichBlocks(md: String): List<RichBlock> {
    val lines = md.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    val blocks = mutableListOf<RichBlock>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        if (line.isBlank()) { i++; continue }

        if (FENCE.matchAt(line.trim(), 0) != null) {
            i++
            val body = mutableListOf<String>()
            while (i < lines.size && FENCE.matchAt(lines[i].trim(), 0) == null) {
                body.add(lines[i]); i++
            }
            i++ // closing fence
            blocks.add(RichBlock.CodeBlock(body.joinToString("\n")))
            continue
        }

        val heading = HEADING.matchAt(line, 0)
        if (heading != null) {
            blocks.add(RichBlock.Heading(heading.groupValues[1].length, parseInline(heading.groupValues[2].trim())))
            i++
            continue
        }

        if (QUOTE.matchAt(line, 0) != null) {
            val q = mutableListOf<String>()
            while (i < lines.size && QUOTE.matchAt(lines[i], 0) != null) {
                q.add(stripPrefix(QUOTE, lines[i])); i++
            }
            blocks.add(RichBlock.Quote(parseInline(q.joinToString("\n"))))
            continue
        }

        if (BULLET.matchAt(line, 0) != null) {
            val items = mutableListOf<List<RichInline>>()
            while (i < lines.size && BULLET.matchAt(lines[i], 0) != null) {
                items.add(parseInline(stripPrefix(BULLET, lines[i]))); i++
            }
            blocks.add(RichBlock.BulletList(items))
            continue
        }

        if (ORDERED.matchAt(line, 0) != null) {
            val items = mutableListOf<List<RichInline>>()
            while (i < lines.size && ORDERED.matchAt(lines[i], 0) != null) {
                items.add(parseInline(stripPrefix(ORDERED, lines[i]))); i++
            }
            blocks.add(RichBlock.OrderedList(items))
            continue
        }

        val para = mutableListOf<String>()
        while (i < lines.size &&
            lines[i].isNotBlank() &&
            HEADING.matchAt(lines[i], 0) == null &&
            BULLET.matchAt(lines[i], 0) == null &&
            ORDERED.matchAt(lines[i], 0) == null &&
            QUOTE.matchAt(lines[i], 0) == null &&
            FENCE.matchAt(lines[i].trim(), 0) == null
        ) {
            para.add(lines[i]); i++
        }
        blocks.add(RichBlock.Paragraph(parseInline(para.joinToString("\n"))))
    }

    return blocks
}

/** Flatten Markdown + mention tokens to readable one-line text (list previews). */
internal fun richTextToPlainText(md: String): String {
    return md
        .replace(Regex("""```(?:.|\n)*?```"""), " ")
        .replace(Regex("""`([^`]+)`""")) { it.groupValues[1] }
        .replace(Regex("""#\[([^\]]+)\]\([^)]+\)""")) { it.groupValues[1] }
        .replace(Regex("""@\[([^\]]+)\]\((?:user|guest|post):[^)]+\)""")) { it.groupValues[1] }
        .replace(Regex("""\[([^\]]+)\]\([^)]+\)""")) { it.groupValues[1] }
        .replace(Regex("""(\*\*|__|~~|\*|_)"""), "")
        .replace(Regex("""(?m)^\s{0,3}#{1,6}\s+"""), "")
        .replace(Regex("""(?m)^\s*[-*+]\s+"""), "")
        .replace(Regex("""(?m)^\s*\d+[.)]\s+"""), "")
        .replace(Regex("""(?m)^\s*>\s?"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
}
