package io.github.kasecrab.razorback.ui.core

import android.view.Choreographer
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Damped harmonic oscillator stepped on the Choreographer. One instance per animated
 * value; [animateTo] retargets mid-flight keeping velocity, so gestures hand off cleanly.
 */
class Spring(
    stiffness: Float = 500f,
    dampingRatio: Float = 1f,
    private val onUpdate: (Float) -> Unit,
) : Choreographer.FrameCallback {

    var value = 0f
        private set
    var velocity = 0f
        private set
    var target = 0f
        private set
    val isRunning: Boolean get() = running

    private val k = stiffness
    private val c = 2f * dampingRatio * sqrt(stiffness)
    private var running = false
    private var lastNanos = 0L
    private var onEnd: (() -> Unit)? = null

    fun snapTo(v: Float) {
        cancel()
        value = v
        target = v
        velocity = 0f
        onUpdate(v)
    }

    fun animateTo(to: Float, initialVelocity: Float = velocity, onEnd: (() -> Unit)? = null) {
        target = to
        velocity = initialVelocity
        this.onEnd = onEnd
        if (!running) {
            running = true
            lastNanos = 0L
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun cancel() {
        if (running) {
            running = false
            Choreographer.getInstance().removeFrameCallback(this)
        }
        onEnd = null
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        var dt = if (lastNanos == 0L) 1f / 60f else (frameTimeNanos - lastNanos) / 1e9f
        lastNanos = frameTimeNanos
        if (dt > 1f / 15f) dt = 1f / 15f
        // Sub-step so stiff springs stay stable at low frame rates.
        var remaining = dt
        while (remaining > 0f) {
            val h = if (remaining > STEP) STEP else remaining
            val a = -k * (value - target) - c * velocity
            velocity += a * h
            value += velocity * h
            remaining -= h
        }
        if (abs(velocity) < REST_VELOCITY && abs(value - target) < REST_DISTANCE) {
            value = target
            velocity = 0f
            running = false
            onUpdate(value)
            val end = onEnd
            onEnd = null
            end?.invoke()
            return
        }
        onUpdate(value)
        Choreographer.getInstance().postFrameCallback(this)
    }

    private companion object {
        const val STEP = 1f / 240f
        const val REST_VELOCITY = 0.005f
        const val REST_DISTANCE = 0.0005f
    }
}
