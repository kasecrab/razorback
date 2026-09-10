package io.github.kasecrab.razorback.ui.md

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.dp

/** Turns parsed markdown into spanned text. Fences and tables are views and never come here at top level. */
class MdRenderer(private val context: Context, private val theme: Theme) {

    private val flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
    private val indent = context.dp(22)
    private val dot = context.dp(2.5f)

    fun render(block: MdBlock): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        block(sb, block, 0)
        trimTrailingNewlines(sb)
        return sb
    }

    private fun block(sb: SpannableStringBuilder, b: MdBlock, depth: Int) {
        when (b) {
            is MdBlock.Paragraph -> {
                inlines(sb, b.inlines)
                sb.append('\n')
            }
            is MdBlock.Heading -> {
                val start = sb.length
                inlines(sb, b.inlines)
                val scale = when (b.level) {
                    1 -> 1.5f
                    2 -> 1.3f
                    3 -> 1.15f
                    else -> 1.05f
                }
                sb.setSpan(HeadingSpan(scale, Fonts.medium), start, sb.length, flags)
                sb.append('\n')
            }
            is MdBlock.ListBlock -> {
                var n = b.start
                for (item in b.items) {
                    val label = if (b.ordered) "$n." else null
                    val start = sb.length
                    var first = true
                    for (child in item.blocks) {
                        val cs = sb.length
                        block(sb, child, depth + 1)
                        if (first) {
                            // Marker only on the item's first paragraph; nested content keeps the indent.
                            sb.setSpan(ListMarkerSpan(indent, label, theme.textSecondary, dot), cs, maxOf(cs + 1, sb.length), flags)
                            first = false
                        } else {
                            sb.setSpan(ListMarkerSpan(indent, null, 0, 0f), cs, maxOf(cs + 1, sb.length), flags)
                        }
                    }
                    if (item.blocks.isEmpty()) {
                        sb.append('​')
                        sb.setSpan(ListMarkerSpan(indent, label, theme.textSecondary, dot), start, sb.length, flags)
                        sb.append('\n')
                    }
                    n++
                }
            }
            is MdBlock.Quote -> {
                val start = sb.length
                for (child in b.blocks) block(sb, child, depth + 1)
                if (sb.length > start) {
                    sb.setSpan(QuoteBarSpan(theme.outline, context.dp(3), context.dp(12)), start, sb.length, flags)
                    sb.setSpan(android.text.style.ForegroundColorSpan(theme.textSecondary), start, sb.length, flags)
                }
            }
            is MdBlock.Fence -> {
                val start = sb.length
                sb.append(b.code.ifEmpty { " " })
                sb.setSpan(TypefaceSpan(Typeface.MONOSPACE), start, sb.length, flags)
                sb.setSpan(RelativeSizeSpan(0.9f), start, sb.length, flags)
                sb.setSpan(BackgroundColorSpan(theme.codeBg), start, sb.length, flags)
                sb.append('\n')
            }
            is MdBlock.Table -> {
                // Inside lists or quotes a table degrades to rows of text.
                val rows = listOf(b.header) + b.rows
                for (row in rows) {
                    for ((i, cell) in row.withIndex()) {
                        if (i > 0) sb.append("  ·  ")
                        inlines(sb, cell)
                    }
                    sb.append('\n')
                }
            }
            is MdBlock.Image -> {
                val start = sb.length
                sb.append(b.alt.ifEmpty { b.url })
                sb.setSpan(LinkSpan(b.url, theme.accent), start, sb.length, flags)
                sb.append('\n')
            }
            MdBlock.Rule -> sb.append("———\n")
        }
    }

    fun inlines(sb: SpannableStringBuilder, list: List<Inline>) {
        for (inl in list) {
            when (inl) {
                is Inline.Text -> sb.append(inl.text)
                is Inline.Code -> {
                    val start = sb.length
                    sb.append(' ').append(inl.code).append(' ')
                    sb.setSpan(TypefaceSpan(Typeface.MONOSPACE), start, sb.length, flags)
                    sb.setSpan(RelativeSizeSpan(0.9f), start, sb.length, flags)
                    sb.setSpan(BackgroundColorSpan(theme.codeBg), start, sb.length, flags)
                }
                is Inline.Strong -> span(sb, inl.children, StyleSpan(Typeface.BOLD))
                is Inline.Em -> span(sb, inl.children, StyleSpan(Typeface.ITALIC))
                is Inline.Strike -> span(sb, inl.children, StrikethroughSpan())
                is Inline.Link -> span(sb, inl.children, LinkSpan(inl.url, theme.accent))
                is Inline.Image -> {
                    val start = sb.length
                    sb.append(inl.alt.ifEmpty { inl.url })
                    sb.setSpan(LinkSpan(inl.url, theme.accent), start, sb.length, flags)
                }
                Inline.SoftBreak, Inline.HardBreak -> sb.append('\n')
            }
        }
    }

    private fun span(sb: SpannableStringBuilder, children: List<Inline>, what: Any) {
        val start = sb.length
        inlines(sb, children)
        if (sb.length > start) sb.setSpan(what, start, sb.length, flags)
    }

    private fun trimTrailingNewlines(sb: SpannableStringBuilder) {
        var end = sb.length
        while (end > 0 && sb[end - 1] == '\n') end--
        if (end < sb.length) sb.delete(end, sb.length)
    }
}
