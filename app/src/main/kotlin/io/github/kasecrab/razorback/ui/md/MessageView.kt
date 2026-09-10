package io.github.kasecrab.razorback.ui.md

import android.content.Context
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp

/**
 * Markdown as a column of block views. On every update only blocks whose source text
 * changed are rebuilt, so a streaming reply re-renders just its last paragraph.
 */
class MessageView(context: Context) : LinearLayout(context), Themed {

    private var renderer = MdRenderer(context, context.appTheme)
    private val sources = ArrayList<String>()
    private val kinds = ArrayList<Int>()
    private val spareText = ArrayList<TextView>(4)
    private var text: String = ""

    init {
        orientation = VERTICAL
    }

    fun render(markdown: String) {
        if (markdown == text && childCount > 0) return
        text = markdown
        val blocks = MdParser.parseTop(markdown)
        var i = 0
        while (i < blocks.size) {
            val tb = blocks[i]
            val kind = kindOf(tb.block)
            if (i < sources.size && kinds[i] == kind && sources[i] == tb.source) {
                i++
                continue
            }
            if (i < sources.size && kinds[i] == kind) {
                bind(getChildAt(i), tb.block)
                sources[i] = tb.source
            } else {
                if (i < sources.size) {
                    recycle(getChildAt(i))
                    removeViewAt(i)
                    sources.removeAt(i)
                    kinds.removeAt(i)
                }
                val v = create(kind)
                bind(v, tb.block)
                addView(v, i, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = if (i == 0) 0 else dp(10) })
                sources.add(i, tb.source)
                kinds.add(i, kind)
            }
            i++
        }
        while (sources.size > blocks.size) {
            val last = sources.size - 1
            recycle(getChildAt(last))
            removeViewAt(last)
            sources.removeAt(last)
            kinds.removeAt(last)
        }
    }

    private fun kindOf(b: MdBlock): Int = when (b) {
        is MdBlock.Fence -> CODE
        is MdBlock.Table -> TABLE
        MdBlock.Rule -> RULE
        else -> TEXT
    }

    private fun create(kind: Int): View = when (kind) {
        CODE -> CodeBlockView(context)
        TABLE -> TableView(context)
        RULE -> View(context).apply {
            setBackgroundColor(context.appTheme.outline)
            minimumHeight = dp(1)
        }
        else -> spareText.removeLastOrNull() ?: TextView(context).apply {
            typeface = Fonts.regular
            setTextIsSelectable(true)
            movementMethod = LinkMovementMethod.getInstance()
            setLineSpacing(0f, 1.18f)
            styleText(this, context.appTheme)
        }
    }

    private fun bind(v: View, b: MdBlock) {
        when (v) {
            is CodeBlockView -> (b as MdBlock.Fence).let { v.set(it.lang, it.code) }
            is TableView -> v.set(b as MdBlock.Table, renderer)
            is TextView -> v.setText(renderer.render(b), TextView.BufferType.SPANNABLE)
        }
    }

    private fun recycle(v: View) {
        if (v is TextView && spareText.size < 4) spareText.add(v)
    }

    private fun styleText(tv: TextView, theme: Theme) {
        tv.setTextColor(theme.textPrimary)
        tv.setLinkTextColor(theme.accent)
        tv.highlightColor = theme.accentSoft
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
    }

    override fun onThemeChanged(theme: Theme) {
        renderer = MdRenderer(context, theme)
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            when (v) {
                is Themed -> v.onThemeChanged(theme)
                is TextView -> styleText(v, theme)
                else -> v.setBackgroundColor(theme.outline)
            }
        }
        for (tv in spareText) styleText(tv, theme)
        // Spans carry colours, so rebuild text blocks from source.
        val t = text
        text = ""
        sources.clear()
        kinds.clear()
        removeAllViews()
        render(t)
    }

    private companion object {
        const val TEXT = 0
        const val CODE = 1
        const val TABLE = 2
        const val RULE = 3
    }
}
