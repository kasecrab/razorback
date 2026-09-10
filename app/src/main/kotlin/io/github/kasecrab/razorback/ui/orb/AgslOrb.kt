package io.github.kasecrab.razorback.ui.orb

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RuntimeShader
import io.github.kasecrab.razorback.ui.core.Theme

/**
 * An orb that is one AGSL shader over one rectangle. Subclasses supply the `main`
 * function; the prelude gives them noise, and the uniforms are shared: `res`, `t`,
 * `inLevel`, `outLevel`, `state`, and colours `c0` (accent), `c1` (light), `c2` (bg).
 */
abstract class AgslOrb(override val id: String, override val name: String, body: String) : Orb {

    private val source = PRELUDE + body
    private val paint = Paint()
    private var shader: RuntimeShader? = null
    private var lastAccent = 0
    private var lastBg = 0

    private var broken = false
    private val fallback = Paint(Paint.ANTI_ALIAS_FLAG)
    private var attached = 0

    override fun onAttach() {
        attached++
        if (shader == null && !broken) {
            try {
                shader = RuntimeShader(source)
                paint.shader = shader
                lastAccent = 0
                lastBg = 0
            } catch (e: IllegalArgumentException) {
                // A shader the GPU driver rejects must not take the screen down with it.
                io.github.kasecrab.razorback.core.Log.e("orb '$id' shader failed to compile", e)
                broken = true
            }
        }
    }

    override fun onDetach() {
        // The same orb can be on screen twice (picker preview and the real thing); the
        // shader lives until the last view lets go.
        attached = maxOf(0, attached - 1)
        if (attached == 0) {
            shader = null
            paint.shader = null
        }
    }

    override fun draw(canvas: Canvas, w: Int, h: Int, t: Float, inLevel: Float, outLevel: Float, state: Int, theme: Theme) {
        val s = shader
        if (s == null) {
            canvas.drawColor(theme.bg)
            fallback.color = theme.accent
            canvas.drawCircle(w / 2f, h / 2f, minOf(w, h) * (0.28f + 0.05f * maxOf(inLevel, outLevel)), fallback)
            return
        }
        if (theme.accent != lastAccent || theme.bg != lastBg) {
            lastAccent = theme.accent
            lastBg = theme.bg
            s.setColorUniform("c0", theme.accent)
            s.setColorUniform("c1", lighten(theme.accent))
            s.setColorUniform("c2", theme.bg)
        }
        s.setFloatUniform("res", w.toFloat(), h.toFloat())
        s.setFloatUniform("t", t)
        s.setFloatUniform("inLevel", inLevel)
        s.setFloatUniform("outLevel", outLevel)
        s.setFloatUniform("state", state.toFloat())
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }

    private fun lighten(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] *= 0.35f
        hsv[2] = 1f
        return Color.HSVToColor(hsv)
    }

    companion object {
        /** Uniforms and value-noise helpers every shader shares. `state`: 0 idle, 1 listening, 2 user, 3 thinking, 4 speaking. */
        val PRELUDE = """
            uniform float2 res;
            uniform float t;
            uniform float inLevel;
            uniform float outLevel;
            uniform float state;
            layout(color) uniform half4 c0;
            layout(color) uniform half4 c1;
            layout(color) uniform half4 c2;

            // Integer-free hash that stays smooth at reduced GPU precision; the classic
            // sin-based one breaks into visible blocks on phones.
            float hash(float2 p) {
                float3 p3 = fract(float3(p.xyx) * 0.1031);
                p3 += dot(p3, p3.yzx + 33.33);
                return fract((p3.x + p3.y) * p3.z);
            }

            float noise(float2 p) {
                float2 i = floor(p);
                float2 f = fract(p);
                float2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
                float a = hash(i);
                float b = hash(i + float2(1.0, 0.0));
                float c = hash(i + float2(0.0, 1.0));
                float d = hash(i + float2(1.0, 1.0));
                return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
            }

            float fbm(float2 p) {
                float v = 0.0;
                float a = 0.5;
                for (int i = 0; i < 4; i++) {
                    v += a * noise(p);
                    p = p * 2.05 + float2(1.7, 9.2);
                    a *= 0.5;
                }
                return v;
            }

            float2 centred(float2 frag) {
                return (frag - res * 0.5) / min(res.x, res.y);
            }

            float thinking() {
                return step(2.5, state) * step(state, 3.5);
            }

        """.trimIndent() + "\n"
    }
}
