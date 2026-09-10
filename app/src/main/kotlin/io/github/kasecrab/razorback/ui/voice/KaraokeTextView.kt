package io.github.kasecrab.razorback.ui.voice

import android.content.Context
import android.graphics.Canvas
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme

/**
 * A paragraph whose words light up as they are spoken. The text is laid out once per
 * change; each frame only moves the boundary between lit and unlit, which is a clip
 * rectangle, so following speech costs three text draws and no allocation.
 */
class KaraokeTextView(context: Context) : View(context), Themed {

    private val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
    private var text = StringBuilder()
    private var layout: StaticLayout? = null
    private var layoutWidth = -1
    private var wordStarts = IntArray(64)
    private var wordEnds = IntArray(64)
    private var litColor = 0
    private var dimColor = 0

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

    fun setText(s: CharSequence) {
        text.setLength(0)
        text.append(s)
        reindex()
        layout = null
        requestLayout()
        invalidate()
    }

    fun append(s: CharSequence) {
        if (text.isNotEmpty() && !text[text.length - 1].isWhitespace()) text.append(' ')
        text.append(s)
        reindex()
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
            }
            wordStarts[n] = start
            wordEnds[n] = i
            n++
        }
        wordCount = n
        if (litWord > n) litWord = n
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
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
        paint.color = dimColor
        l.draw(canvas)
        val word = litWord
        if (word < 0 || wordCount == 0) return
        paint.color = litColor
        if (word >= wordCount) {
            l.draw(canvas)
            return
        }
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
        val x = x0 + (x1 - x0) * litFraction
        canvas.save()
        canvas.clipRect(0f, lineTop.toFloat(), x, lineBottom.toFloat())
        l.draw(canvas)
        canvas.restore()
    }
}
