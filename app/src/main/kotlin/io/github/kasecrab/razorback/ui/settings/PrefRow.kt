package io.github.kasecrab.razorback.ui.settings

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.icon
import io.github.kasecrab.razorback.ui.widget.Shapes

/** Tappable row that leads to another screen: icon, title, subtitle, chevron. */
class NavRow(context: Context) : LinearLayout(context), Themed {

    private val icon = ImageView(context)
    private val title = TextView(context)
    private val subtitle = TextView(context)
    private val chevron = ImageView(context)
    private var iconRes = 0

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        minimumHeight = dp(60)
        setPadding(dp(16), dp(10), dp(12), dp(10))

        icon.scaleType = ImageView.ScaleType.CENTER
        addView(icon, LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(16) })

        val texts = LinearLayout(context)
        texts.orientation = VERTICAL
        title.typeface = Fonts.regular
        title.maxLines = 1
        subtitle.typeface = Fonts.regular
        subtitle.maxLines = 2
        texts.addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        texts.addView(subtitle, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(texts, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        chevron.scaleType = ImageView.ScaleType.CENTER
        addView(chevron, LayoutParams(dp(24), dp(24)).apply { marginStart = dp(8) })
        onThemeChanged(context.appTheme)
    }

    fun set(iconRes: Int, titleText: CharSequence, subtitleText: CharSequence? = null) {
        this.iconRes = iconRes
        title.text = titleText
        subtitle.text = subtitleText
        subtitle.visibility = if (subtitleText.isNullOrEmpty()) View.GONE else View.VISIBLE
        onThemeChanged(context.appTheme)
    }

    override fun onThemeChanged(theme: Theme) {
        background = Shapes.ripple(theme.accentSoft, null, 0f)
        title.setTextColor(theme.textPrimary)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
        subtitle.setTextColor(theme.textSecondary)
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.SECONDARY))
        if (iconRes != 0) icon.setImageDrawable(context.icon(iconRes, theme.textSecondary))
        chevron.setImageDrawable(context.icon(R.drawable.ic_chevron_right, theme.textTertiary))
    }
}
