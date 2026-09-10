package io.github.kasecrab.razorback.ui.widget

import android.content.Context
import android.util.TypedValue
import android.widget.TextView
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp

/** Small uppercase label that introduces a group of settings. */
class SectionHeader(context: Context) : TextView(context), Themed {
    init {
        typeface = Fonts.medium
        isAllCaps = true
        letterSpacing = 0.06f
        setPadding(dp(16), dp(20), dp(16), dp(8))
        onThemeChanged(context.appTheme)
    }

    override fun onThemeChanged(theme: Theme) {
        setTextColor(theme.textTertiary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.CAPTION))
    }
}

/** Secondary explanatory text or a status line. */
class Caption(context: Context) : TextView(context), Themed {

    enum class Tone { NORMAL, ACCENT, DANGER }

    var tone: Tone = Tone.NORMAL
        set(value) {
            field = value
            onThemeChanged(context.appTheme)
        }

    init {
        typeface = Fonts.regular
        onThemeChanged(context.appTheme)
    }

    override fun onThemeChanged(theme: Theme) {
        setTextColor(
            when (tone) {
                Tone.NORMAL -> theme.textSecondary
                Tone.ACCENT -> theme.accent
                Tone.DANGER -> theme.danger
            },
        )
        setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.SECONDARY))
    }
}
