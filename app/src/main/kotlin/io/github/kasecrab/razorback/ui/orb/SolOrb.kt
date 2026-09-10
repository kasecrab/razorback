package io.github.kasecrab.razorback.ui.orb

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RuntimeShader
import io.github.kasecrab.razorback.ui.core.Theme

/**
 * A soft sphere of drifting cloud, lit from the accent. The whole picture is one AGSL
 * shader over one rectangle: the GPU does the work, the CPU only updates uniforms.
 */
class SolOrb : Orb {

    override val id = "sol"
    override val name = "Sol"

    private val paint = Paint()
    private var shader: RuntimeShader? = null
    private var lastAccent = 0
    private var lastBg = 0

    override fun onAttach() {
        if (shader == null) {
            shader = RuntimeShader(SOURCE)
            paint.shader = shader
            lastAccent = 0
        }
    }

    override fun onDetach() {
        shader = null
        paint.shader = null
    }

    override fun draw(canvas: Canvas, w: Int, h: Int, t: Float, inLevel: Float, outLevel: Float, state: Int, theme: Theme) {
        val s = shader ?: return
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
        s.setFloatUniform("state", if (state == Orb.THINKING) 1f else 0f)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }

    private fun lighten(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] *= 0.35f
        hsv[2] = 1f
        return Color.HSVToColor(hsv)
    }

    private companion object {
        val SOURCE = """
            uniform float2 res;
            uniform float t;
            uniform float inLevel;
            uniform float outLevel;
            uniform float state;
            layout(color) uniform half4 c0;
            layout(color) uniform half4 c1;
            layout(color) uniform half4 c2;

            float hash(float2 p) {
                return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
            }

            float noise(float2 p) {
                float2 i = floor(p);
                float2 f = fract(p);
                float2 u = f * f * (3.0 - 2.0 * f);
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

            half4 main(float2 frag) {
                float2 uv = (frag - res * 0.5) / min(res.x, res.y);
                float r = length(uv);
                float level = max(inLevel, outLevel);
                float breathe = 0.012 * sin(t * 1.3) + 0.02 * state * sin(t * 4.0);
                float n = fbm(uv * 2.4 + float2(t * 0.13, -t * 0.09));
                float wobble = (n - 0.5) * (0.06 + 0.22 * level);
                float radius = 0.30 + breathe + 0.05 * outLevel + wobble;
                float body = 1.0 - smoothstep(radius - 0.025, radius + 0.015, r);
                float2 q = uv * 1.9 + float2(t * 0.07, t * 0.045);
                float cloud = fbm(q + n * 0.7);
                float3 base = mix(float3(c0.rgb), float3(c1.rgb), smoothstep(0.30, 0.78, cloud));
                base = mix(base, float3(1.0), inLevel * inLevel * 0.35);
                float3 glow = float3(c0.rgb) * exp(-r * 6.5) * (0.22 + 0.55 * outLevel + 0.15 * state);
                float3 col = mix(float3(c2.rgb) + glow, base, body);
                return half4(half3(col), 1.0);
            }
        """.trimIndent()
    }
}
