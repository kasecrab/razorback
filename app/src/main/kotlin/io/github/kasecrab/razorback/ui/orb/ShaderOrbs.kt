package io.github.kasecrab.razorback.ui.orb

/** The ring gate: a dark torus with lit nodes running round it and a swirl of light inside. */
class RingOrb : AgslOrb(
    "ring",
    "Ring",
    """
    half4 main(float2 frag) {
        float2 uv = centred(frag);
        float r = length(uv);
        float a = atan(uv.y, uv.x);
        float level = max(inLevel, outLevel);
        float think = thinking();
        float rot = t * (0.35 + 1.4 * outLevel + 0.9 * think);
        float ringR = 0.36 + 0.02 * outLevel;
        float ring = 1.0 - smoothstep(0.034, 0.05, abs(r - ringR));
        float3 hull = mix(float3(c2.rgb), float3(0.5), 0.18);
        float nodes = pow(0.5 + 0.5 * sin(a * 13.0 - rot), 24.0);
        float sparks = pow(0.5 + 0.5 * sin(a * 41.0 + rot * 2.3), 60.0) * inLevel;
        float3 ringCol = mix(hull, float3(c0.rgb), clamp(nodes * (0.55 + 0.45 * level) + sparks, 0.0, 1.0));
        float2 s = uv * 3.0;
        float sw = fbm(s + float2(cos(t * 0.2), sin(t * 0.2)) * 0.6 + a * 0.15);
        float gate = exp(-r * 5.5) * (0.22 + outLevel * 0.9 + inLevel * 0.5 + think * 0.3) * (0.55 + 0.45 * sw);
        float3 col = float3(c2.rgb) + float3(c0.rgb) * gate * (1.0 - ring) * step(r, ringR + 0.01);
        col += float3(c1.rgb) * exp(-abs(r - ringR) * 38.0) * (0.18 + 0.3 * level) * (1.0 - ring);
        col = mix(col, ringCol, ring);
        return half4(half3(col), 1.0);
    }
    """.trimIndent(),
)

/** Layered sine strips that swell with the voice. */
class PulseOrb : AgslOrb(
    "pulse",
    "Pulse",
    """
    half4 main(float2 frag) {
        float2 uv = centred(frag);
        float level = 0.12 + max(inLevel * 0.9, outLevel) + 0.15 * thinking();
        float3 col = float3(c2.rgb);
        float env = exp(-uv.x * uv.x * 7.0);
        for (int i = 0; i < 4; i++) {
            float fi = float(i);
            float f = 2.6 + fi * 1.45;
            float sp = 1.1 + fi * 0.4;
            float ph = fi * 1.9;
            float amp = (0.025 + 0.24 * level) * env * (1.0 - fi * 0.12);
            float y = sin(uv.x * f * 6.2831 + t * sp * 2.0 + ph) * amp;
            float d = abs(uv.y - y);
            float line = exp(-d * d * 2600.0);
            float3 tint = mix(float3(c0.rgb), float3(c1.rgb), fi / 3.0);
            col += tint * line * (0.85 - fi * 0.12);
        }
        return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
    }
    """.trimIndent(),
)

/** Gas swirling through three hues around the accent. */
class NebulaOrb : AgslOrb(
    "nebula",
    "Nebula",
    """
    float3 hueShift(float3 c, float s) {
        float3 k = float3(0.57735);
        float cs = cos(s);
        float sn = sin(s);
        return c * cs + cross(k, c) * sn + k * dot(k, c) * (1.0 - cs);
    }

    half4 main(float2 frag) {
        float2 uv = centred(frag);
        float r = length(uv);
        float level = max(inLevel, outLevel);
        float think = thinking();
        float2 p = uv * 2.2;
        float2 warp = float2(fbm(p + float2(t * 0.11, 0.0)), fbm(p + float2(0.0, t * 0.09) + 5.2));
        float n = fbm(p + warp * (1.2 + 1.5 * level) + float2(t * 0.05, -t * 0.03));
        float3 a = float3(c0.rgb);
        float3 b = hueShift(a, 0.7);
        float3 c = hueShift(a, -0.7);
        float3 gas = mix(mix(c, a, smoothstep(0.25, 0.55, n)), b, smoothstep(0.55, 0.85, n));
        gas = mix(gas, float3(c1.rgb), pow(n, 3.0) * (0.4 + 0.6 * outLevel));
        float disc = 1.0 - smoothstep(0.28 + 0.05 * level, 0.40 + 0.05 * level, r);
        float glow = exp(-r * 4.5) * (0.15 + 0.5 * outLevel + 0.2 * think);
        float3 col = float3(c2.rgb) + a * glow;
        col = mix(col, gas, disc);
        return half4(half3(col), 1.0);
    }
    """.trimIndent(),
)

/** A dark body with a corona that flares when the assistant speaks. */
class EclipseOrb : AgslOrb(
    "eclipse",
    "Eclipse",
    """
    half4 main(float2 frag) {
        float2 uv = centred(frag);
        float r = length(uv);
        float a = atan(uv.y, uv.x);
        float level = max(inLevel, outLevel);
        float think = thinking();
        float discR = 0.27;
        float disc = 1.0 - smoothstep(discR - 0.004, discR + 0.004, r);
        float ang = fbm(float2(a * 2.2 + t * 0.15, r * 3.0 - t * 0.35));
        float corona = exp(-(r - discR) * (9.0 - 4.0 * outLevel)) * (0.35 + 0.9 * outLevel + 0.25 * think) * (0.5 + 0.8 * ang);
        float flare = pow(0.5 + 0.5 * sin(a * 7.0 + t * 0.8), 18.0) * exp(-(r - discR) * 3.0) * outLevel * 0.8;
        float rim = exp(-abs(r - discR) * 60.0) * (0.6 + 0.4 * inLevel);
        float3 col = float3(c2.rgb) + float3(c0.rgb) * (corona + flare) * step(discR, r) + float3(c1.rgb) * rim;
        float3 body = mix(float3(0.0), float3(c0.rgb) * 0.08, inLevel);
        col = mix(col, body, disc);
        return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
    }
    """.trimIndent(),
)

/** Concentric rings pushed outward by the voice. */
class ReactorOrb : AgslOrb(
    "reactor",
    "Reactor",
    """
    half4 main(float2 frag) {
        float2 uv = centred(frag);
        float r = length(uv);
        float level = max(inLevel, outLevel);
        float think = thinking();
        float core = exp(-r * 14.0) * (0.8 + 0.6 * level + 0.4 * think * (0.5 + 0.5 * sin(t * 6.0)));
        float rings = pow(0.5 + 0.5 * sin(r * 46.0 - t * (2.5 + 6.0 * outLevel)), 6.0) * exp(-r * 4.0) * (0.15 + 0.8 * level);
        float wave = fract(t * (0.35 + 0.6 * outLevel));
        float shock = exp(-abs(r - wave * 0.55) * 60.0) * (1.0 - wave) * (0.3 + 0.9 * outLevel);
        float grid = pow(0.5 + 0.5 * sin(atan(uv.y, uv.x) * 24.0 + t), 40.0) * exp(-r * 6.0) * inLevel * 0.6;
        float3 col = float3(c2.rgb) + float3(c0.rgb) * (rings + shock + grid) + float3(c1.rgb) * core;
        return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
    }
    """.trimIndent(),
)
