package io.github.kasecrab.razorback.ui.orb

/** A soft sphere of drifting cloud, lit from the accent. */
class SolOrb : AgslOrb(
    "sol",
    "Sol",
    """
    half4 main(float2 frag) {
        float2 uv = centred(frag);
        float r = length(uv);
        float level = max(inLevel, outLevel);
        float think = thinking();
        float breathe = 0.012 * sin(t * 1.3) + 0.02 * think * sin(t * 4.0);
        float n = fbm(uv * 2.4 + float2(t * 0.13, -t * 0.09));
        float wobble = (n - 0.5) * (0.06 + 0.22 * level);
        float radius = 0.30 + breathe + 0.05 * outLevel + wobble;
        float body = 1.0 - smoothstep(radius - 0.025, radius + 0.015, r);
        float2 q = uv * 1.9 + float2(t * 0.07, t * 0.045);
        float cloud = fbm(q + n * 0.7);
        float3 base = mix(float3(c0.rgb), float3(c1.rgb), smoothstep(0.30, 0.78, cloud));
        base = mix(base, float3(1.0), inLevel * inLevel * 0.35);
        float3 glow = float3(c0.rgb) * exp(-r * 6.5) * (0.22 + 0.55 * outLevel + 0.15 * think);
        float3 col = mix(float3(c2.rgb) + glow, base, body);
        return half4(half3(col), 1.0);
    }
    """.trimIndent(),
)
