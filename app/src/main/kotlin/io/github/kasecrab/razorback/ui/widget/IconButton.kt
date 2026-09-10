package io.github.kasecrab.razorback.ui.widget

import android.content.Context
import android.widget.ImageView
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.icon

/** 44 dp round touch target around a 24 dp tinted vector. */
class IconButton(context: Context) : ImageView(context), Themed {

    enum class Tone { PRIMARY, SECONDARY, ACCENT, ON_ACCENT, DANGER }

    var iconRes: Int = 0
        set(value) {
            if (field == value) return
            field = value
            render(context.appTheme)
        }

    var tone: Tone = Tone.SECONDARY
        set(value) {
            if (field == value) return
            field = value
            render(context.appTheme)
        }

    /** Filled circle behind the icon, used for the send button. */
    var filled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            render(context.appTheme)
        }

    init {
        scaleType = ScaleType.CENTER
        isClickable = true
        isFocusable = true
        minimumWidth = dp(44)
        minimumHeight = dp(44)
        onThemeChanged(context.appTheme)
    }

    override fun onThemeChanged(theme: Theme) {
        render(theme)
    }

    private fun render(theme: Theme) {
        val tint = when (tone) {
            Tone.PRIMARY -> theme.textPrimary
            Tone.SECONDARY -> theme.textSecondary
            Tone.ACCENT -> theme.accent
            Tone.ON_ACCENT -> theme.onAccent
            Tone.DANGER -> theme.danger
        }
        if (iconRes != 0) setImageDrawable(context.icon(iconRes, tint))
        background = if (filled) {
            Shapes.ripple(theme.accentSoft, Shapes.pill(if (tone == Tone.ON_ACCENT) theme.accent else theme.surface), 999f)
        } else {
            Shapes.circleRipple(theme.accentSoft)
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.38f
    }
}
