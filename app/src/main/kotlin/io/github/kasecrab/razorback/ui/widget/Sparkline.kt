package io.github.kasecrab.razorback.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp

/** A small line chart with a soft fill; the path is rebuilt only when data or size changes. */
class Sparkline(context: Context) : View(context), Themed {

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val baseline = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = dp(1f) }
    private val path = Path()
    private val area = Path()
    private var values = DoubleArray(0)
    private var accent = 0
    private var dirty = true

    init {
        onThemeChanged(context.appTheme)
    }

    fun setData(v: DoubleArray) {
        values = v
        dirty = true
        invalidate()
    }

    override fun onThemeChanged(theme: Theme) {
        accent = theme.accent
        line.color = accent
        baseline.color = theme.outline
        dirty = true
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        dirty = true
    }

    private fun rebuild() {
        dirty = false
        path.reset()
        area.reset()
        val n = values.size
        if (n < 2 || width == 0) return
        val max = values.max().takeIf { it > 0 } ?: 1.0
        val padY = dp(4f)
        val h = height - padY * 2
        val stepX = width.toFloat() / (n - 1)
        for (i in 0 until n) {
            val x = i * stepX
            val y = padY + (h - (values[i] / max * h)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        area.addPath(path)
        area.lineTo(width.toFloat(), height.toFloat())
        area.lineTo(0f, height.toFloat())
        area.close()
        fill.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), (accent and 0x00FFFFFF) or 0x55000000, 0, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        if (dirty) rebuild()
        canvas.drawLine(0f, height - 0.5f, width.toFloat(), height - 0.5f, baseline)
        if (values.size < 2) return
        canvas.drawPath(area, fill)
        canvas.drawPath(path, line)
    }
}
