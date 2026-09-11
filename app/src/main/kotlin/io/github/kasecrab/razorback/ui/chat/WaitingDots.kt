package io.github.kasecrab.razorback.ui.chat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Choreographer
import android.view.View
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import kotlin.math.sin

/** Three dots that rise and fall in turn while a reply is on its way. Frames only while shown. */
class WaitingDots(context: Context) : View(context), Themed, Choreographer.FrameCallback {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var startNanos = 0L
    private var t = 0f
    private var running = false

    init {
        onThemeChanged(context.appTheme)
    }

    override fun onThemeChanged(theme: Theme) {
        paint.color = theme.textTertiary
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(dp(40), dp(20))

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        resume()
    }

    override fun onDetachedFromWindow() {
        pause()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isShown) resume() else pause()
    }

    private fun resume() {
        if (running || !isAttachedToWindow || !isShown) return
        running = true
        startNanos = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun pause() {
        if (!running) return
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (startNanos == 0L) startNanos = frameTimeNanos
        t = (frameTimeNanos - startNanos) / 1e9f
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDraw(canvas: Canvas) {
        val r = dp(3f)
        val gap = dp(9f)
        val cy = height / 2f
        val cx0 = width / 2f - gap
        for (i in 0 until 3) {
            // Each dot is a third of a cycle behind the last; a 0.9 s cycle reads as patient, not frantic.
            val phase = (t / 0.9f - i * 0.16f) * (2f * Math.PI.toFloat())
            val lift = (sin(phase) + 1f) / 2f
            paint.alpha = (90 + 165 * lift).toInt()
            canvas.drawCircle(cx0 + i * gap, cy - dp(3f) * lift, r, paint)
        }
    }
}
