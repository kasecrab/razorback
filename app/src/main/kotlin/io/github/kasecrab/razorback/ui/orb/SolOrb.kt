package io.github.kasecrab.razorback.ui.orb

/**
 * A lit sphere of slowly turning cloud: dark accent in the folds, light accent on the
 * crests, a highlight up and to the left, a thin bright rim and a halo that widens when
 * the assistant speaks. Everything moves at once, slowly, so it reads as alive rather
 * than as a flat disc with a blurred edge.
 */
class SolOrb : AgslOrb(
    "sol",
    "Sol",
    """
    half4 main(float2 frag) {
        float2 uv = centred(frag);
        float r = length(uv);
        float level = max(inLevel, outLevel);
        float think = thinking();
        float breathe = 0.010 * sin(t * 1.1) + 0.018 * think * sin(t * 3.7);
        float radius = 0.27 + breathe + 0.055 * outLevel + 0.03 * inLevel;
        float2 q = uv / radius;
        float rq = length(q);

        // Two layers of cloud drifting in different directions; the second is warped by the first.
        float n1 = fbm(q * 1.5 + float2(t * 0.075, -t * 0.05));
        float n2 = fbm(q * 2.4 - float2(t * 0.04, t * 0.065) + (n1 - 0.5) * 1.1);
        float3 accent = float3(c0.rgb);
        float3 light = float3(c1.rgb);
        float3 deep = accent * 0.62;
        float3 col = mix(deep, accent, smoothstep(0.22, 0.68, n1));
        col = mix(col, light, smoothstep(0.5, 0.95, n2) * (0.5 + 0.5 * level));

        // Sphere shading: a soft highlight and a darker limb give it volume.
        float2 lightAt = float2(-0.38, -0.42);
        float hl = exp(-dot(q - lightAt, q - lightAt) * 1.6);
        col += light * hl * 0.32;
        col *= 1.0 - 0.3 * smoothstep(0.6, 1.0, rq);
        // The person's voice brightens the whole body a little.
        col = mix(col, light, inLevel * inLevel * 0.3);

        // Anti-aliased edge one and a half pixels wide, whatever the size.
        float aa = 1.5 / min(res.x, res.y);
        float body = 1.0 - smoothstep(radius - aa, radius + aa, r);
        float rim = exp(-abs(r - radius) * (min(res.x, res.y) * 0.05)) * 0.55;

        // Halo: wider and brighter while speaking, pulsing while thinking.
        float haloStrength = 0.16 + 0.5 * outLevel + 0.22 * inLevel + 0.14 * think * (0.5 + 0.5 * sin(t * 3.0));
        float halo = exp(-(r - radius) * (8.0 - 3.0 * outLevel)) * step(radius, r) * haloStrength;
        float3 bg = float3(c2.rgb) + accent * halo;
        float3 outc = mix(bg, col, body);
        outc = mix(outc, light, rim * (1.0 - body) * 0.6 + rim * body * 0.4);
        return half4(half3(outc), 1.0);
    }
    """.trimIndent(),
)
