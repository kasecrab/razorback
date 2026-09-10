package io.github.kasecrab.razorback.ui.md

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp

/**
 * A fenced code block drawn in one View: header with language and copy, monospace body
 * laid out at its natural width and panned sideways. No nested scroll views to fight.
 */
class CodeBlockView(context: Context) : View(context), Themed {

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.medium }
    private val codePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.mono }
    private val rect = RectF()
    private val pan = HorizontalPan(this)

    private val headerH = dp(34)
    private val padX = dp(14)
    private val padY = dp(12)
    private val radius = dp(12f)

    private var lang: String? = null
    private var code: String = ""
    private var layout: StaticLayout? = null
    private var codeWidth = 0
    private var copyLabel = "Copy"
    private var copiedUntil = 0L
    private var textColor = 0
    private var mutedColor = 0

    init {
        onThemeChanged(context.appTheme)
    }

    fun set(lang: String?, code: String) {
        val changed = code != this.code
        this.lang = lang
        this.code = code
        if (changed) {
            layout = null
            requestLayout()
            invalidate()
        }
    }

    override fun onThemeChanged(theme: Theme) {
        bg.color = theme.codeBg
        outline.color = theme.outline
        outline.strokeWidth = dp(1f)
        textColor = theme.textPrimary
        mutedColor = theme.textSecondary
        headerPaint.color = mutedColor
        headerPaint.textSize = context.dp(theme.sp(Type.CAPTION))
        codePaint.color = textColor
        codePaint.textSize = context.dp(theme.sp(Type.SECONDARY))
        layout = null
        requestLayout()
        invalidate()
    }

    private fun ensureLayout(): StaticLayout {
        layout?.let { return it }
        var w = 0f
        var s = 0
        val text = code.ifEmpty { " " }
        while (s <= text.length) {
            val e = text.indexOf('\n', s).let { if (it < 0) text.length else it }
            w = maxOf(w, codePaint.measureText(text, s, e))
            s = e + 1
        }
        codeWidth = w.toInt() + 1
        val l = StaticLayout.Builder.obtain(text, 0, text.length, codePaint, maxOf(codeWidth, 1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
        layout = l
        return l
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val l = ensureLayout()
        val h = headerH + padY * 2 + l.height
        setMeasuredDimension(w, h)
        pan.maxScroll = codeWidth + padX * 2 - w
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(0.5f, 0.5f, w - 0.5f, h - 0.5f)
        canvas.drawRoundRect(rect, radius, radius, bg)
        canvas.drawRoundRect(rect, radius, radius, outline)
        canvas.drawLine(0f, headerH.toFloat(), w, headerH.toFloat(), outline)

        val hy = headerH / 2f - (headerPaint.descent() + headerPaint.ascent()) / 2f
        canvas.drawText(lang ?: "code", padX.toFloat(), hy, headerPaint)
        val label = if (System.currentTimeMillis() < copiedUntil) "Copied" else copyLabel
        canvas.drawText(label, w - padX - headerPaint.measureText(label), hy, headerPaint)

        val l = ensureLayout()
        canvas.save()
        canvas.clipRect(1f, headerH + 1f, w - 1f, h - 1f)
        canvas.translate(padX - pan.scrollX.toFloat(), headerH + padY.toFloat())
        l.draw(canvas)
        canvas.restore()
    }

    override fun computeScroll() = pan.computeScroll()

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_UP && ev.y < headerH && ev.x > width * 0.6f) {
            copy()
            return true
        }
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && ev.y < headerH && ev.x > width * 0.6f) return true
        return pan.onTouchEvent(ev) || super.onTouchEvent(ev)
    }

    private fun copy() {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("code", code))
        copiedUntil = System.currentTimeMillis() + 1500
        invalidate()
        postDelayed({ invalidate() }, 1600)
    }
}
