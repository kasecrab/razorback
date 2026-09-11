package io.github.kasecrab.razorback.ui.core

import android.graphics.Color

enum class ThemeMode { LIGHT, DARK, AMOLED }

class Accent(val name: String, val color: Int)

object Accents {
    val all = listOf(
        Accent("Indigo", 0xFF6366F1.toInt()),
        Accent("Sky", 0xFF0EA5E9.toInt()),
        Accent("Emerald", 0xFF10B981.toInt()),
        Accent("Amber", 0xFFF59E0B.toInt()),
        Accent("Rose", 0xFFF43F5E.toInt()),
        Accent("Violet", 0xFF8B5CF6.toInt()),
        Accent("Graphite", 0xFF71717A.toInt()),
    )
    val default = all[0]
}

/**
 * Every colour, radius, duration and type size the UI reads. Immutable: switching
 * themes builds a new instance and [ThemeHost] walks the view tree with it.
 */
class Theme(
    val mode: ThemeMode,
    val bg: Int,
    val surface: Int,
    val surfaceElevated: Int,
    val outline: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val textTertiary: Int,
    val accent: Int,
    val onAccent: Int,
    val accentSoft: Int,
    val danger: Int,
    /** Green for a check that passed. */
    val ok: Int,
    val codeBg: Int,
    val userBubble: Int,
    val scrim: Int,
    val fontScale: Float = 1f,
    val reduceMotion: Boolean = false,
) {
    val isLight: Boolean get() = mode == ThemeMode.LIGHT

    val radiusS = 8f
    val radiusM = 12f
    val radiusL = 20f
    val radiusXl = 28f

    val durShort: Long get() = if (reduceMotion) 0 else 120
    val durMedium: Long get() = if (reduceMotion) 0 else 200
    val durLong: Long get() = if (reduceMotion) 0 else 280

    fun sp(size: Float): Float = size * fontScale

    companion object {
        fun build(
            mode: ThemeMode,
            accent: Int = Accents.default.color,
            fontScale: Float = 1f,
            reduceMotion: Boolean = false,
        ): Theme {
            val onAccent = if (Color.luminance(accent) > 0.5f) 0xFF0F0F10.toInt() else Color.WHITE
            val accentSoft = (accent and 0x00FFFFFF) or 0x24000000
            return when (mode) {
                ThemeMode.LIGHT -> Theme(
                    mode = mode,
                    bg = 0xFFFFFFFF.toInt(),
                    surface = 0xFFF4F4F5.toInt(),
                    surfaceElevated = 0xFFFFFFFF.toInt(),
                    outline = 0xFFE4E4E7.toInt(),
                    textPrimary = 0xFF0F0F10.toInt(),
                    textSecondary = 0xFF52525B.toInt(),
                    textTertiary = 0xFFA1A1AA.toInt(),
                    accent = accent,
                    onAccent = onAccent,
                    accentSoft = accentSoft,
                    danger = 0xFFDC2626.toInt(),
                    ok = 0xFF16A34A.toInt(),
                    codeBg = 0xFFF4F4F5.toInt(),
                    userBubble = 0xFFF4F4F5.toInt(),
                    scrim = 0x66000000,
                    fontScale = fontScale,
                    reduceMotion = reduceMotion,
                )
                ThemeMode.DARK -> Theme(
                    mode = mode,
                    bg = 0xFF0E0E11.toInt(),
                    surface = 0xFF16161A.toInt(),
                    surfaceElevated = 0xFF1D1D22.toInt(),
                    outline = 0xFF26262C.toInt(),
                    textPrimary = 0xFFF4F4F5.toInt(),
                    textSecondary = 0xFFA1A1AA.toInt(),
                    textTertiary = 0xFF6B6B75.toInt(),
                    accent = accent,
                    onAccent = onAccent,
                    accentSoft = accentSoft,
                    danger = 0xFFF87171.toInt(),
                    ok = 0xFF4ADE80.toInt(),
                    codeBg = 0xFF0A0A0C.toInt(),
                    userBubble = 0xFF1F1F24.toInt(),
                    scrim = 0x99000000.toInt(),
                    fontScale = fontScale,
                    reduceMotion = reduceMotion,
                )
                ThemeMode.AMOLED -> Theme(
                    mode = mode,
                    bg = 0xFF000000.toInt(),
                    surface = 0xFF0A0A0A.toInt(),
                    surfaceElevated = 0xFF121212.toInt(),
                    outline = 0xFF1F1F1F.toInt(),
                    textPrimary = 0xFFF4F4F5.toInt(),
                    textSecondary = 0xFFA1A1AA.toInt(),
                    textTertiary = 0xFF6B6B75.toInt(),
                    accent = accent,
                    onAccent = onAccent,
                    accentSoft = accentSoft,
                    danger = 0xFFF87171.toInt(),
                    ok = 0xFF4ADE80.toInt(),
                    codeBg = 0xFF0A0A0A.toInt(),
                    userBubble = 0xFF141414.toInt(),
                    scrim = 0xB3000000.toInt(),
                    fontScale = fontScale,
                    reduceMotion = reduceMotion,
                )
            }
        }
    }
}
