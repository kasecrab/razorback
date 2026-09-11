package io.github.kasecrab.razorback.ui.orb

import android.content.Context
import android.graphics.Canvas
import android.view.Choreographer
import android.view.View
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.appTheme

/**
 * Hosts an [Orb]. Frames are requested only while attached and visible, and paced down
 * when nothing moves so an idle voice screen costs almost nothing.
 */
class OrbView(context: Context) : View(context), Themed {

    var orb: Orb = Orbs.all[0]
        set(value) {
            if (field === value) return
            if (isAttachedToWindow) field.onDetach()
            field = value
            if (isAttachedToWindow) value.onAttach()
            invalidate()
        }

    var state: Int = Orb.IDLE
        set(value) {
            field = value
            invalidate()
        }

    /** Raw levels, read on each frame; 0..1. */
    var inLevel: () -> Float = { 0f }
    var outLevel: () -> Float = { 0f }

    /** Thumbnails in a picker run slower; there are several of them and none is the point. */
    var preview = false

    private var theme: Theme = context.appTheme
    private var running = false
    private var startNanos = 0L
    private var lastDrawNanos = 0L
    private var smoothIn = 0f
    private var smoothOut = 0f
    private var time = 0f

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            if (startNanos == 0L) startNanos = frameTimeNanos
            val rawIn = inLevel().coerceIn(0f, 1f)
            val rawOut = outLevel().coerceIn(0f, 1f)
            smoothIn += (rawIn - smoothIn) * (if (rawIn > smoothIn) 0.45f else 0.12f)
            smoothOut += (rawOut - smoothOut) * (if (rawOut > smoothOut) 0.45f else 0.12f)
            val active = smoothIn > 0.03f || smoothOut > 0.03f || state == Orb.THINKING
            // Full rate only while sound moves it or it speaks; waiting for the person, and
            // an orb in a picker, run at half rate, and a truly idle orb at its own pace.
            val fps = when {
                theme.reduceMotion -> 15
                preview || state == Orb.LISTENING -> 30
                active || state == Orb.SPEAKING -> 60
                else -> orb.idleFps
            }
            val interval = 1_000_000_000L / fps
            if (frameTimeNanos - lastDrawNanos >= interval - 1_000_000L) {
                lastDrawNanos = frameTimeNanos
                time = (frameTimeNanos - startNanos) / 1e9f
                invalidate()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        onThemeChanged(context.appTheme)
    }

    override fun onThemeChanged(theme: Theme) {
        this.theme = theme
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        orb.onAttach()
        resume()
    }

    override fun onDetachedFromWindow() {
        pause()
        orb.onDetach()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) resume() else pause()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isShown && windowVisibility == VISIBLE) resume() else pause()
    }

    private fun resume() {
        if (running || !isAttachedToWindow) return
        running = true
        Choreographer.getInstance().postFrameCallback(frame)
    }

    private fun pause() {
        if (!running) return
        running = false
        Choreographer.getInstance().removeFrameCallback(frame)
    }

    override fun onDraw(canvas: Canvas) {
        orb.draw(canvas, width, height, time, smoothIn, smoothOut, state, theme)
    }
}
