package io.github.kasecrab.razorback.ui.orb

import android.graphics.Canvas
import android.graphics.Paint
import io.github.kasecrab.razorback.ui.core.Theme
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A wireframe sphere of points, rotated on the CPU and drawn as two point batches. The
 * cheapest orb, and the one used when motion is reduced.
 */
class LatticeOrb : Orb {

    override val id = "lattice"
    override val name = "Lattice"
    override val idleFps: Int get() = 15

    private val base = FloatArray(POINTS * 3)
    private val front = FloatArray(POINTS * 2)
    private val back = FloatArray(POINTS * 2)
    private val paintFront = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val paintBack = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val paintGlow = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        // Fibonacci sphere: even spread with no clumping at the poles.
        val golden = Math.PI * (3.0 - sqrt(5.0))
        for (i in 0 until POINTS) {
            val y = 1.0 - (i / (POINTS - 1.0)) * 2.0
            val radius = sqrt(1.0 - y * y)
            val theta = golden * i
            base[i * 3] = (cos(theta) * radius).toFloat()
            base[i * 3 + 1] = y.toFloat()
            base[i * 3 + 2] = (sin(theta) * radius).toFloat()
        }
    }

    override fun draw(canvas: Canvas, w: Int, h: Int, t: Float, inLevel: Float, outLevel: Float, state: Int, theme: Theme) {
        val level = maxOf(inLevel, outLevel)
        val think = if (state == Orb.THINKING) 1f else 0f
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) * (0.27f + 0.04f * outLevel + 0.01f * sin(t * 1.3f))
        val ay = t * (0.35f + 1.2f * outLevel + 0.8f * think)
        val ax = 0.4f + 0.25f * sin(t * 0.3f)
        val cy1 = cos(ay)
        val sy1 = sin(ay)
        val cx1 = cos(ax)
        val sx1 = sin(ax)
        var nf = 0
        var nb = 0
        val jitter = 1f + 0.12f * inLevel
        for (i in 0 until POINTS) {
            val x0 = base[i * 3] * jitter
            val y0 = base[i * 3 + 1]
            val z0 = base[i * 3 + 2] * jitter
            val x1 = x0 * cy1 + z0 * sy1
            val z1 = -x0 * sy1 + z0 * cy1
            val y2 = y0 * cx1 - z1 * sx1
            val z2 = y0 * sx1 + z1 * cx1
            val depth = (z2 + 1f) / 2f
            val px = cx + x1 * r * (0.85f + 0.15f * depth)
            val py = cy + y2 * r * (0.85f + 0.15f * depth)
            if (z2 >= 0f) {
                front[nf++] = px
                front[nf++] = py
            } else {
                back[nb++] = px
                back[nb++] = py
            }
        }
        canvas.drawColor(theme.bg)
        paintGlow.color = theme.accent
        paintGlow.alpha = (40 + 120 * outLevel).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, r * 0.55f, paintGlow)
        paintBack.color = theme.accent
        paintBack.alpha = 70
        paintBack.strokeWidth = r * 0.018f
        canvas.drawPoints(back, 0, nb, paintBack)
        paintFront.color = theme.accent
        paintFront.alpha = (170 + 85 * level).toInt().coerceIn(0, 255)
        paintFront.strokeWidth = r * (0.028f + 0.02f * inLevel)
        canvas.drawPoints(front, 0, nf, paintFront)
    }

    private companion object {
        const val POINTS = 600
    }
}
