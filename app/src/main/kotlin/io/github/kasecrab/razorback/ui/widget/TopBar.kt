package io.github.kasecrab.razorback.ui.widget

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.icon
import android.widget.LinearLayout

/** 56 dp bar: leading icon, centred title, optional trailing icon. */
class TopBar(context: Context) : FrameLayout(context), Themed {

    val leading = IconButton(context)
    val title = TextView(context)
    val subtitle = TextView(context)
    val trailing = IconButton(context)
    private val titles = LinearLayout(context)

    init {
        leading.tone = IconButton.Tone.PRIMARY
        trailing.tone = IconButton.Tone.PRIMARY
        addView(leading, LayoutParams(dp(44), dp(44), Gravity.START or Gravity.CENTER_VERTICAL).apply { marginStart = dp(6) })
        title.typeface = Fonts.medium
        title.gravity = Gravity.CENTER
        title.maxLines = 1
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        title.compoundDrawablePadding = dp(4)
        subtitle.typeface = Fonts.regular
        subtitle.gravity = Gravity.CENTER
        subtitle.maxLines = 1
        subtitle.ellipsize = android.text.TextUtils.TruncateAt.END
        subtitle.visibility = View.GONE
        titles.orientation = LinearLayout.VERTICAL
        titles.gravity = Gravity.CENTER
        titles.addView(title, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        titles.addView(subtitle, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(titles, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            marginStart = dp(56)
            marginEnd = dp(56)
        })
        addView(trailing, LayoutParams(dp(44), dp(44), Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = dp(6) })
        trailing.visibility = View.GONE
        minimumHeight = dp(56)
        onThemeChanged(context.appTheme)
    }

    fun set(leadingIcon: Int, leadingDescription: String, text: CharSequence, trailingIcon: Int = 0, trailingDescription: String? = null) {
        leading.iconRes = leadingIcon
        leading.contentDescription = leadingDescription
        title.text = text
        if (trailingIcon != 0) {
            trailing.iconRes = trailingIcon
            trailing.contentDescription = trailingDescription
            trailing.visibility = View.VISIBLE
        } else {
            trailing.visibility = View.GONE
        }
    }

    /** The title becomes a button: a chevron after the text and a ripple behind it. */
    fun makeTitleClickable(onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
        titles.isClickable = true
        titles.setPadding(dp(12), dp(4), dp(8), dp(4))
        titles.setOnClickListener {
            io.github.kasecrab.razorback.ui.core.Haptics.tick(titles)
            onClick()
        }
        if (onLongClick != null) {
            titles.setOnLongClickListener {
                onLongClick()
                true
            }
        }
        chevron = true
        onThemeChanged(context.appTheme)
    }

    fun setSubtitle(text: CharSequence?) {
        subtitle.text = text
        subtitle.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private var chevron = false

    override fun onThemeChanged(theme: Theme) {
        title.setTextColor(theme.textPrimary)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
        subtitle.setTextColor(theme.textTertiary)
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.CAPTION))
        if (chevron) {
            val d = context.icon(io.github.kasecrab.razorback.R.drawable.ic_chevron_down, theme.textSecondary)
            d.setBounds(0, 0, dp(16), dp(16))
            title.setCompoundDrawablesRelative(null, null, d, null)
            titles.background = io.github.kasecrab.razorback.ui.widget.Shapes.ripple(theme.accentSoft, null, dp(theme.radiusM))
        }
    }
}
