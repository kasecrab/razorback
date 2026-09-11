package io.github.kasecrab.razorback.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp

/** Horizontal slider drawn by hand, with optional steps and a tick on each step. */
class SliderView(context: Context) : View(context), Themed {

    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumb = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var lastStep = -1

    var min = 0f
    var max = 1f
    var step = 0f
    var value = 0f
        set(v) {
            field = v.coerceIn(min, max)
            invalidate()
        }
    var onChange: ((Float) -> Unit)? = null

    init {
        onThemeChanged(context.appTheme)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(40))

    override fun onThemeChanged(theme: Theme) {
        track.color = theme.outline
        fill.color = theme.accent
        thumb.color = theme.accent
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val pad = dp(12f)
        val cy = height / 2f
        val h = dp(4f)
        rect.set(pad, cy - h / 2, width - pad, cy + h / 2)
        canvas.drawRoundRect(rect, h, h, track)
        val f = if (max > min) (value - min) / (max - min) else 0f
        val x = pad + (width - pad * 2) * f
        rect.set(pad, cy - h / 2, x, cy + h / 2)
        canvas.drawRoundRect(rect, h, h, fill)
        canvas.drawCircle(x, cy, dp(10f), thumb)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {}
            else -> return false
        }
        val pad = dp(12f)
        var f = ((event.x - pad) / (width - pad * 2)).coerceIn(0f, 1f)
        var v = min + (max - min) * f
        if (step > 0f) {
            v = min + Math.round((v - min) / step) * step
            val s = Math.round((v - min) / step)
            if (s != lastStep) {
                lastStep = s
                io.github.kasecrab.razorback.ui.core.Haptics.tick(this)
            }
        }
        if (v != value) {
            value = v
            onChange?.invoke(value)
        }
        return true
    }
}
