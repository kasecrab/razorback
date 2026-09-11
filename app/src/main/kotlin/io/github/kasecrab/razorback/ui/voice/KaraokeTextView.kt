package io.github.kasecrab.razorback.ui.voice

import android.content.Context
import android.graphics.Canvas
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.view.animation.AnimationUtils
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp

/**
 * A paragraph whose words appear one after another as they arrive, and light up as they
 * are spoken. The text is laid out once per change. Each frame draws the settled words
 * through the layout under two clip rectangles, dim then lit, and only the handful of
 * words still fading in are drawn on their own, so following speech costs a few text
 * draws and no allocation.
 */
class KaraokeTextView(context: Context) : View(context), Themed {

    private val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
    private var text = StringBuilder()
    private var layout: StaticLayout? = null
    private var layoutWidth = -1
    private var wordStarts = IntArray(64)
    private var wordEnds = IntArray(64)
    /** When each word starts to appear, on the animation clock; words shown at once are at zero. */
    private var wordShownAt = LongArray(64)
    /** Words before this index have fully appeared; it only moves forward. */
    private var settled = 0
    /** When the last scheduled word starts to appear; the next batch follows on from it. */
    private var lastShownAt = 0L
    private var litColor = 0
    private var dimColor = 0
    private val rise = dp(3f)

    /** Number of whitespace-separated words in the text. */
    var wordCount = 0
        private set

    /** Index of the word being spoken; -1 lights nothing, [wordCount] lights everything. */
    var litWord = -1
        private set

    /** How far through [litWord] the voice is, 0..1. */
    var litFraction = 0f
        private set

    init {
        paint.typeface = Fonts.regular
        onThemeChanged(context.appTheme)
    }

    /** Replaces the text, shown at once. */
    fun setText(s: CharSequence) {
        text.setLength(0)
        text.append(s)
        reindex()
        for (i in 0 until wordCount) wordShownAt[i] = 0L
        settled = wordCount
        lastShownAt = 0L
        layout = null
        requestLayout()
        invalidate()
    }

    /** Adds text whose words appear one after the other, following on from any still appearing. */
    fun append(s: CharSequence) {
        if (text.isNotEmpty() && !text[text.length - 1].isWhitespace()) text.append(' ')
        text.append(s)
        val before = wordCount
        reindex()
        val now = AnimationUtils.currentAnimationTimeMillis()
        if (lastShownAt - now > MAX_LAG_MS) {
            // Words are arriving faster than they appear; squeeze what is still queued so
            // the page never runs more than a beat behind the voice.
            val span = (lastShownAt - now).toFloat()
            for (i in settled until before) {
                val ahead = wordShownAt[i] - now
                if (ahead > 0) wordShownAt[i] = now + (ahead / span * MAX_LAG_MS).toLong()
            }
            lastShownAt = now + MAX_LAG_MS
        }
        var at = maxOf(now, lastShownAt + STAGGER_MS)
        for (i in before until wordCount) {
            wordShownAt[i] = at
            at += STAGGER_MS
        }
        if (wordCount > before) lastShownAt = at - STAGGER_MS
        layout = null
        requestLayout()
        invalidate()
    }

    fun text(): String = text.toString()

    /** Light everything up to and including [word], and [fraction] of it. */
    fun setProgress(word: Int, fraction: Float) {
        val w = word.coerceIn(-1, wordCount)
        val f = fraction.coerceIn(0f, 1f)
        if (w == litWord && f == litFraction) return
        litWord = w
        litFraction = f
        invalidate()
    }

    fun lightAll() = setProgress(wordCount, 1f)

    /** Top of the line holding [word], in this view's pixels; for keeping it in view. */
    fun lineTopOf(word: Int): Int {
        val l = layout ?: return 0
        if (word < 0 || word >= wordCount) return 0
        return l.getLineTop(l.getLineForOffset(wordStarts[word])) + paddingTop
    }

    fun lineBottomOf(word: Int): Int {
        val l = layout ?: return height
        if (word < 0 || word >= wordCount) return height
        return l.getLineBottom(l.getLineForOffset(wordStarts[word])) + paddingTop
    }

    private fun reindex() {
        var n = 0
        var i = 0
        val len = text.length
        while (i < len) {
            while (i < len && text[i].isWhitespace()) i++
            if (i >= len) break
            val start = i
            while (i < len && !text[i].isWhitespace()) i++
            if (n == wordStarts.size) {
                wordStarts = wordStarts.copyOf(n * 2)
                wordEnds = wordEnds.copyOf(n * 2)
                wordShownAt = wordShownAt.copyOf(n * 2)
            }
            wordStarts[n] = start
            wordEnds[n] = i
            n++
        }
        wordCount = n
        if (litWord > n) litWord = n
        if (settled > n) settled = n
    }

    override fun onThemeChanged(theme: Theme) {
        litColor = theme.textPrimary
        dimColor = (theme.textSecondary and 0x00FFFFFF) or 0x8C000000.toInt()
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY + 2f), resources.displayMetrics)
        layout = null
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val inner = maxOf(0, w - paddingLeft - paddingRight)
        if (layout == null || layoutWidth != inner) {
            layoutWidth = inner
            layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxOf(inner, 1))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.22f)
                .setIncludePad(false)
                .build()
        }
        val h = (layout?.height ?: 0) + paddingTop + paddingBottom
        setMeasuredDimension(w, resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val l = layout ?: return
        if (wordCount == 0) return
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
        val now = AnimationUtils.currentAnimationTimeMillis()
        while (settled < wordCount && wordShownAt[settled] + FADE_MS <= now) settled++
        // Whatever is being spoken is shown in full, arrived or not: the voice cannot wait for the page.
        val shown = maxOf(settled, minOf(litWord + 1, wordCount))
        if (shown > 0) {
            paint.color = dimColor
            drawUpTo(canvas, l, shown - 1, 1f)
        }
        if (litWord >= 0) {
            paint.color = litColor
            if (litWord >= wordCount) l.draw(canvas) else drawUpTo(canvas, l, litWord, litFraction)
        }
        var i = shown
        while (i < wordCount) {
            val t = (now - wordShownAt[i]).toFloat() / FADE_MS
            if (t <= 0f) break
            val k = 1f - (1f - t) * (1f - t)
            val line = l.getLineForOffset(wordStarts[i])
            paint.color = dimColor
            paint.alpha = (DIM_ALPHA * k).toInt()
            canvas.drawText(text, wordStarts[i], wordEnds[i], l.getPrimaryHorizontal(wordStarts[i]), l.getLineBaseline(line) + rise * (1f - k), paint)
            i++
        }
        if (shown < wordCount) {
            val next = wordShownAt[shown] - now
            if (next > 16L) postInvalidateDelayed(next) else postInvalidateOnAnimation()
        }
    }

    /** The layout up to [fraction] of [word]: every line above it whole, its own line up to the point. */
    private fun drawUpTo(canvas: Canvas, l: StaticLayout, word: Int, fraction: Float) {
        val line = l.getLineForOffset(wordStarts[word])
        val lineTop = l.getLineTop(line)
        val lineBottom = l.getLineBottom(line)
        if (lineTop > 0) {
            canvas.save()
            canvas.clipRect(0, 0, l.width, lineTop)
            l.draw(canvas)
            canvas.restore()
        }
        val x0 = l.getPrimaryHorizontal(wordStarts[word])
        val x1 = l.getPrimaryHorizontal(wordEnds[word])
        val x = x0 + (x1 - x0) * fraction
        canvas.save()
        canvas.clipRect(0f, lineTop.toFloat(), x, lineBottom.toFloat())
        l.draw(canvas)
        canvas.restore()
    }

    private companion object {
        /** One word after another, a beat apart, each fading up over a quarter second. */
        const val STAGGER_MS = 26L
        const val FADE_MS = 260L
        /** The most the queue of words still to appear is allowed to stretch. */
        const val MAX_LAG_MS = 1000L
        const val DIM_ALPHA = 0x8C
    }
}
