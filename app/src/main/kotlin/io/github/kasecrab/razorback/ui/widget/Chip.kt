package io.github.kasecrab.razorback.ui.widget

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Haptics
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.icon

/** Compact pill with optional leading and trailing icons. */
class Chip(context: Context) : TextView(context), Themed {

    enum class Style { OUTLINE, SOFT, ACCENT, PLAIN }

    var style: Style = Style.OUTLINE
        set(value) {
            if (field == value) return
            field = value
            render(context.appTheme)
        }

    var leadingIcon: Int = 0
        set(value) {
            field = value
            render(context.appTheme)
        }

    var trailingIcon: Int = 0
        set(value) {
            field = value
            render(context.appTheme)
        }

    /** Highlighted while a selection or toggle is active. */
    var active: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            render(context.appTheme)
        }

    init {
        typeface = Fonts.medium
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        compoundDrawablePadding = dp(6)
        setPadding(dp(12), 0, dp(12), 0)
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        minimumHeight = dp(32)
        onThemeChanged(context.appTheme)
    }

    override fun performClick(): Boolean {
        val handled = super.performClick()
        if (handled) Haptics.tick()
        return handled
    }

    override fun onThemeChanged(theme: Theme) {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.SECONDARY))
        render(theme)
    }

    private fun render(theme: Theme) {
        val fg: Int
        val bg: Int
        var stroke = 0
        when {
            style == Style.ACCENT || (active && style != Style.PLAIN) -> {
                fg = if (style == Style.ACCENT) theme.onAccent else theme.accent
                bg = if (style == Style.ACCENT) theme.accent else theme.accentSoft
            }
            style == Style.SOFT -> {
                fg = theme.textPrimary
                bg = theme.surface
            }
            style == Style.PLAIN -> {
                fg = if (active) theme.accent else theme.textSecondary
                bg = 0
            }
            else -> {
                fg = theme.textPrimary
                bg = 0
                stroke = dp(1)
            }
        }
        setTextColor(fg)
        val lead = if (leadingIcon != 0) context.icon(leadingIcon, fg).also { it.setBounds(0, 0, dp(18), dp(18)) } else null
        val trail = if (trailingIcon != 0) context.icon(trailingIcon, fg).also { it.setBounds(0, 0, dp(16), dp(16)) } else null
        setCompoundDrawablesRelative(lead, null, trail, null)
        val shape = if (bg != 0 || stroke > 0) Shapes.pill(bg, stroke, theme.outline) else null
        background = Shapes.ripple(theme.accentSoft, shape, 999f)
    }
}
