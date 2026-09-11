package io.github.kasecrab.razorback.ui.orb

/**
 * A lit sphere of slowly turning cloud: dark accent in the folds, light accent on the
 * crests, lit from up and to the left with a tight highlight, a crisp rim and a halo that
 * widens when the assistant speaks. Everything outside the halo is transparent, so the
 * orb sits on whatever is behind it instead of in a square of its own.
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
        float radius = 0.30 + breathe + 0.05 * outLevel + 0.03 * inLevel;
        float2 q = uv / radius;
        float rq2 = dot(q, q);
        float px = 1.0 / min(res.x, res.y);

        // Sphere normal, so light and cloud wrap round a body instead of lying on a disc.
        float z = sqrt(max(0.0, 1.0 - rq2));
        float3 n = float3(q, z);
        float2 wrap = q / (z + 0.35);

        // Cloud in two drifting layers, the second warped by the first, plus fine grain
        // so the surface stays sharp however large it is drawn.
        float n1 = fbm(wrap * 2.1 + float2(t * 0.07, -t * 0.045));
        float n2 = fbm(wrap * 3.6 - float2(t * 0.04, t * 0.06) + (n1 - 0.5) * 1.3);
        float grain = noise(wrap * 11.0 + float2(t * 0.02, t * 0.03)) - 0.5;
        float3 accent = float3(c0.rgb);
        float3 light = float3(c1.rgb);
        float3 deep = accent * 0.5;
        float3 col = mix(deep, accent, smoothstep(0.3, 0.62, n1 + grain * 0.12));
        col = mix(col, light, smoothstep(0.52, 0.9, n2 + grain * 0.1) * (0.55 + 0.45 * level));

        // Lighting from up-left: diffuse over the body, a tight specular, and a dark limb.
        float3 l = normalize(float3(-0.5, -0.6, 0.62));
        float diff = clamp(dot(n, l), 0.0, 1.0);
        float spec = pow(clamp(dot(n, normalize(l + float3(0.0, 0.0, 1.0))), 0.0, 1.0), 40.0);
        col = col * (0.42 + 0.7 * diff) + light * spec * 0.45;
        col *= 1.0 - 0.35 * smoothstep(0.55, 1.0, rq2);
        col = mix(col, light, inLevel * inLevel * 0.3);

        // A crisp edge one pixel wide, a thin bright rim just inside it, and a halo
        // outside that widens while speaking and pulses while thinking.
        float body = 1.0 - smoothstep(radius - px, radius + px, r);
        float rim = exp(-abs(r - radius + px * 1.5) * (0.08 / px)) * 0.5;
        float haloStrength = 0.14 + 0.5 * outLevel + 0.22 * inLevel + 0.14 * think * (0.5 + 0.5 * sin(t * 3.0));
        float halo = exp(-(r - radius) * (9.0 - 3.0 * outLevel)) * step(radius, r) * haloStrength;
        float3 rgb = col * body + light * rim * body * 0.5 + accent * halo;
        float alpha = clamp(body + halo, 0.0, 1.0);
        return half4(half3(rgb), alpha);
    }
    """.trimIndent(),
)
