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

/** 56 dp bar: leading icon, centred title, optional trailing icon. */
class TopBar(context: Context) : FrameLayout(context), Themed {

    val leading = IconButton(context)
    val title = TextView(context)
    val trailing = IconButton(context)

    init {
        leading.tone = IconButton.Tone.PRIMARY
        trailing.tone = IconButton.Tone.PRIMARY
        addView(leading, LayoutParams(dp(44), dp(44), Gravity.START or Gravity.CENTER_VERTICAL).apply { marginStart = dp(6) })
        title.typeface = Fonts.medium
        title.gravity = Gravity.CENTER
        title.maxLines = 1
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        addView(title, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
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

    override fun onThemeChanged(theme: Theme) {
        title.setTextColor(theme.textPrimary)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
    }
}
