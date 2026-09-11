package io.github.kasecrab.razorback.ui.voice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.orb.OrbView
import io.github.kasecrab.razorback.voice.VoiceSession
import kotlin.math.exp
import kotlin.math.sin

/**
 * A small orb inside a ring that says what the session is doing without a word: the ring
 * swells with the person's voice, breathes while waiting for them, orbits while the model
 * thinks, pulses in the text colour while it speaks, spins while connecting and turns red
 * on an error. One or two strokes a frame, and no frames at all when nothing moves.
 */
class VoiceHalo(context: Context) : FrameLayout(context), Themed, Choreographer.FrameCallback {

    val orb = OrbView(context)

    var state: VoiceSession.State = VoiceSession.State.IDLE
        set(value) {
            if (field == value) return
            field = value
            ripple = 1f
            invalidate()
            schedule()
        }

    /** Raw levels 0..1, read on each frame. */
    var inLevel: () -> Float = { 0f }
    var outLevel: () -> Float = { 0f }

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val bounds = RectF()
    private var accent = 0
    private var ink = 0
    private var faint = 0
    private var danger = 0
    private var level = 0f
    private var ripple = 1f
    private var angle = 0f
    private var breath = 0f
    private var running = false
    private var lastNanos = 0L

    init {
        clipChildren = false
        addView(orb, LayoutParams(dp(ORB_DP), dp(ORB_DP), Gravity.CENTER))
        onThemeChanged(context.appTheme)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val side = MeasureSpec.makeMeasureSpec(dp(HALO_DP), MeasureSpec.EXACTLY)
        super.onMeasure(side, side)
    }

    override fun onThemeChanged(theme: Theme) {
        accent = theme.accent
        ink = theme.textPrimary
        faint = theme.textTertiary
        danger = theme.danger
        orb.onThemeChanged(theme)
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        schedule()
    }

    override fun onDetachedFromWindow() {
        stopFrames()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) schedule() else stopFrames()
    }

    private fun schedule() {
        if (running || !isAttachedToWindow || windowVisibility != VISIBLE) return
        running = true
        lastNanos = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun stopFrames() {
        if (!running) return
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val fps = when (state) {
            VoiceSession.State.USER_SPEAKING, VoiceSession.State.SPEAKING, VoiceSession.State.THINKING, VoiceSession.State.SEARCHING -> 60
            // Waiting, the ring still answers the mic at once; half rate is plenty for that.
            VoiceSession.State.CONNECTING, VoiceSession.State.RECONNECTING, VoiceSession.State.IDLE, VoiceSession.State.LISTENING -> 30
            VoiceSession.State.ERROR -> 0
        }
        if (fps == 0) {
            running = false
            return
        }
        Choreographer.getInstance().postFrameCallback(this)
        // Slower states skip vsyncs rather than run a timer; the clock still measures real time.
        if (lastNanos != 0L && frameTimeNanos - lastNanos < 1_000_000_000L / fps - 1_000_000L) return
        val dt = if (lastNanos == 0L) 1f / fps else ((frameTimeNanos - lastNanos) / 1e9f).coerceAtMost(0.1f)
        lastNanos = frameTimeNanos
        // Speech sits at a few percent of full scale, so the mic is lifted with a soft
        // knee: a quiet word already shows, a shout does not blow the ring out.
        val raw = when (state) {
            VoiceSession.State.LISTENING, VoiceSession.State.USER_SPEAKING -> 1f - exp(-inLevel() * 9f)
            VoiceSession.State.SPEAKING -> 1f - exp(-outLevel() * 6f)
            else -> 0f
        }.coerceIn(0f, 1f)
        level += (raw - level) * if (raw > level) 0.6f else 0.12f
        if (state == VoiceSession.State.USER_SPEAKING || state == VoiceSession.State.LISTENING) {
            ripple += dt * 1.4f
            if (ripple >= 1f && level > 0.3f) ripple = 0f
        }
        angle = (angle + dt * 240f) % 360f
        breath += dt * 2.2f
        invalidate()
    }

    /** The ring goes on after the orb: its square canvas would otherwise cover the ring at the corners. */
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val base = dp(ORB_DP) / 2f + dp(6f)
        when (state) {
            VoiceSession.State.LISTENING, VoiceSession.State.USER_SPEAKING -> {
                // Breathing while quiet; the person's voice swells the ring and sends ripples out.
                val s = (sin(breath) + 1f) / 2f
                if (ripple < 1f) {
                    ring.color = accent
                    ring.alpha = (120 * (1f - ripple)).toInt()
                    ring.strokeWidth = dp(1.5f)
                    canvas.drawCircle(cx, cy, base + dp(5f) + dp(16f) * ripple, ring)
                }
                ring.color = accent
                ring.alpha = (80 + 50 * s + 125 * level).toInt().coerceAtMost(255)
                ring.strokeWidth = dp(1.5f) + dp(2.5f) * level
                canvas.drawCircle(cx, cy, base + dp(1.5f) * s + dp(8f) * level, ring)
            }
            VoiceSession.State.THINKING, VoiceSession.State.SEARCHING -> {
                val r = base + dp(2f)
                bounds.set(cx - r, cy - r, cx + r, cy + r)
                ring.color = accent
                ring.alpha = 230
                ring.strokeWidth = dp(2.5f)
                canvas.drawArc(bounds, angle, 80f, false, ring)
                canvas.drawArc(bounds, angle + 180f, 80f, false, ring)
            }
            VoiceSession.State.SPEAKING -> {
                ring.color = ink
                ring.alpha = 150 + (105 * level).toInt()
                ring.strokeWidth = dp(2.5f) + dp(1.5f) * level
                canvas.drawCircle(cx, cy, base + dp(5f) * level, ring)
            }
            VoiceSession.State.IDLE, VoiceSession.State.CONNECTING, VoiceSession.State.RECONNECTING -> {
                val r = base + dp(2f)
                bounds.set(cx - r, cy - r, cx + r, cy + r)
                ring.color = faint
                ring.alpha = 200
                ring.strokeWidth = dp(2f)
                canvas.drawArc(bounds, angle, 270f, false, ring)
            }
            VoiceSession.State.ERROR -> {
                ring.color = danger
                ring.alpha = 255
                ring.strokeWidth = dp(2f)
                canvas.drawCircle(cx, cy, base + dp(2f), ring)
            }
        }
    }

    private companion object {
        const val ORB_DP = 64
        const val HALO_DP = 104
    }
}
