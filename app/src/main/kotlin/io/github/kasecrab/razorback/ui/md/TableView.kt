package io.github.kasecrab.razorback.ui.md

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.SpannableStringBuilder
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

/** A markdown table: columns sized to content (capped), rows wrap, wide tables pan sideways. */
class TableView(context: Context) : View(context), Themed {

    private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.regular }
    private val headPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.medium }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val headBg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val pan = HorizontalPan(this)

    private val cellPadX = dp(10)
    private val cellPadY = dp(8)
    private val radius = dp(10f)

    private var table: MdBlock.Table? = null
    private var renderer: MdRenderer? = null
    private var cells: Array<Array<StaticLayout>> = emptyArray()
    private var colWidths = IntArray(0)
    private var rowHeights = IntArray(0)
    private var builtFor = -1
    private var totalWidth = 0

    init {
        onThemeChanged(context.appTheme)
    }

    fun set(t: MdBlock.Table, r: MdRenderer) {
        table = t
        renderer = r
        builtFor = -1
        requestLayout()
        invalidate()
    }

    override fun onThemeChanged(theme: Theme) {
        bodyPaint.color = theme.textPrimary
        headPaint.color = theme.textPrimary
        bodyPaint.textSize = context.dp(theme.sp(Type.SECONDARY))
        headPaint.textSize = bodyPaint.textSize
        grid.color = theme.outline
        grid.strokeWidth = dp(1f)
        headBg.color = theme.surface
        builtFor = -1
        requestLayout()
        invalidate()
    }

    private fun build(availableWidth: Int) {
        val t = table ?: return
        val r = renderer ?: return
        if (builtFor == availableWidth) return
        builtFor = availableWidth
        val rows = ArrayList<List<List<Inline>>>(t.rows.size + 1)
        rows.add(t.header)
        rows.addAll(t.rows)
        val cols = t.header.size
        val cap = maxOf(dp(80), (availableWidth * 0.6f).toInt())
        val texts = Array(rows.size) { ri -> Array(cols) { ci -> SpannableStringBuilder().also { sb -> rows[ri].getOrNull(ci)?.let { r.inlines(sb, it) } } } }
        colWidths = IntArray(cols)
        for (ci in 0 until cols) {
            var w = 0f
            for (ri in rows.indices) {
                val p = if (ri == 0) headPaint else bodyPaint
                w = maxOf(w, Layout.getDesiredWidth(texts[ri][ci], p))
            }
            colWidths[ci] = minOf(cap, w.toInt() + 1) + cellPadX * 2
        }
        cells = Array(rows.size) { ri ->
            Array(cols) { ci ->
                val p = if (ri == 0) headPaint else bodyPaint
                val txt = texts[ri][ci]
                StaticLayout.Builder.obtain(txt, 0, txt.length, p, maxOf(1, colWidths[ci] - cellPadX * 2))
                    .setAlignment(
                        when (t.aligns.getOrNull(ci)) {
                            Align.CENTER -> Layout.Alignment.ALIGN_CENTER
                            Align.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                            else -> Layout.Alignment.ALIGN_NORMAL
                        },
                    )
                    .setIncludePad(false)
                    .build()
            }
        }
        rowHeights = IntArray(rows.size) { ri -> (cells[ri].maxOfOrNull { it.height } ?: 0) + cellPadY * 2 }
        totalWidth = colWidths.sum()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec)
        build(available)
        // A narrow table hugs its columns instead of drawing a border across the whole line.
        val w = minOf(available, totalWidth + 2)
        setMeasuredDimension(w, rowHeights.sum() + 2)
        pan.maxScroll = totalWidth + 2 - w
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.save()
        rect.set(0.5f, 0.5f, w - 0.5f, h - 0.5f)
        canvas.clipRect(rect)
        canvas.translate(-pan.scrollX.toFloat(), 0f)
        var y = 1f
        for (ri in cells.indices) {
            val rh = rowHeights[ri].toFloat()
            if (ri == 0) canvas.drawRect(0f, y, totalWidth.toFloat(), y + rh, headBg)
            var x = 0f
            for (ci in cells[ri].indices) {
                canvas.save()
                canvas.translate(x + cellPadX, y + cellPadY)
                cells[ri][ci].draw(canvas)
                canvas.restore()
                x += colWidths[ci]
                if (ci < cells[ri].size - 1) canvas.drawLine(x, y, x, y + rh, grid)
            }
            y += rh
            if (ri < cells.size - 1) canvas.drawLine(0f, y, totalWidth.toFloat(), y, grid)
        }
        canvas.restore()
        canvas.drawRoundRect(rect, radius, radius, grid)
    }

    override fun computeScroll() = pan.computeScroll()

    override fun onTouchEvent(ev: MotionEvent): Boolean = pan.onTouchEvent(ev) || super.onTouchEvent(ev)
}
