package io.github.kasecrab.razorback.ui.md

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.LeadingMarginSpan
import android.text.style.MetricAffectingSpan
import android.view.View
import io.github.kasecrab.razorback.core.Log

/** Bigger and heavier text for headings; the size scales relative to the body. */
class HeadingSpan(private val scale: Float, private val typeface: Typeface) : MetricAffectingSpan() {
    override fun updateMeasureState(p: TextPaint) = apply(p)
    override fun updateDrawState(p: TextPaint) = apply(p)
    private fun apply(p: TextPaint) {
        p.textSize *= scale
        p.typeface = typeface
    }
}

/** A rounded bar down the left of a quote. */
class QuoteBarSpan(private val color: Int, private val bar: Int, private val gap: Int) : LeadingMarginSpan {
    override fun getLeadingMargin(first: Boolean): Int = bar + gap

    override fun drawLeadingMargin(
        c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout,
    ) {
        val old = p.color
        val style = p.style
        p.color = color
        p.style = Paint.Style.FILL
        val left = if (dir > 0) x.toFloat() else (x - bar).toFloat()
        c.drawRoundRect(left, top.toFloat(), left + bar, bottom.toFloat(), bar / 2f, bar / 2f, p)
        p.color = old
        p.style = style
    }
}

/** Bullet dot or ordinal for a list item, indented by depth. */
class ListMarkerSpan(
    private val indent: Int,
    private val label: String?,
    private val color: Int,
    private val dot: Float,
) : LeadingMarginSpan {
    override fun getLeadingMargin(first: Boolean): Int = indent

    override fun drawLeadingMargin(
        c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout,
    ) {
        if (!first) return
        val old = p.color
        val style = p.style
        val align = p.textAlign
        p.color = color
        p.style = Paint.Style.FILL
        if (label == null) {
            val cx = x + dir * (indent - dot * 2.2f)
            val cy = (top + bottom) / 2f
            c.drawCircle(cx, cy, dot, p)
        } else {
            p.textAlign = if (dir > 0) Paint.Align.RIGHT else Paint.Align.LEFT
            c.drawText(label, x + dir * (indent - dot * 1.2f), baseline.toFloat(), p)
        }
        p.color = old
        p.style = style
        p.textAlign = align
    }
}

/** Opens the link in the browser; drawn in the accent without an underline. */
class LinkSpan(val url: String, private val color: Int) : ClickableSpan() {
    override fun onClick(widget: View) {
        try {
            widget.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w("no handler for $url")
        }
    }

    override fun updateDrawState(ds: TextPaint) {
        ds.color = color
        ds.isUnderlineText = false
    }
}
