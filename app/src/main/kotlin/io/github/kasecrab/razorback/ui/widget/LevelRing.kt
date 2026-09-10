package io.github.kasecrab.razorback.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Choreographer
import android.view.View
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.appTheme
import kotlin.math.sin

/**
 * A soft disc that swells with a sound level, drawn behind a button to show it is
 * hearing something. Frames run only while [active]; nothing is allocated per frame.
 */
class LevelRing(context: Context) : View(context), Themed {

    /** 0..1, read every frame. */
    var level: () -> Float = { 0f }

    /** Pulses on its own instead of following the level, e.g. while a socket is still dialling. */
    var waiting = false

    var active = false
        set(value) {
            if (field == value) return
            field = value
            if (value) {
                smooth = 0f
                Choreographer.getInstance().postFrameCallback(frame)
            } else {
                Choreographer.getInstance().removeFrameCallback(frame)
            }
            invalidate()
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var smooth = 0f
    private var t = 0f
    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!active) return
            t = (frameTimeNanos % 4_000_000_000L) / 1e9f
            val target = if (waiting) 0.25f + 0.2f * sin(t * 4f) else level().coerceIn(0f, 1f)
            smooth += (target - smooth) * (if (target > smooth) 0.5f else 0.15f)
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        onThemeChanged(context.appTheme)
    }

    override fun onThemeChanged(theme: Theme) {
        fill.color = theme.accentSoft
        ring.color = theme.accent
        ring.strokeWidth = resources.displayMetrics.density * 1.5f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        active = false
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (!active) return
        val cx = width / 2f
        val cy = height / 2f
        val inner = minOf(width, height) * 0.36f
        val outer = minOf(width, height) * 0.5f
        val r = inner + (outer - inner) * smooth
        canvas.drawCircle(cx, cy, r, fill)
        ring.alpha = (60 + 160 * smooth).toInt()
        canvas.drawCircle(cx, cy, r, ring)
    }
}
