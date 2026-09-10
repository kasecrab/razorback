package io.github.kasecrab.razorback.ui.drawer

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.widget.Avatar
import io.github.kasecrab.razorback.ui.widget.IconButton
import io.github.kasecrab.razorback.ui.widget.Shapes

/** Sidebar contents: chats above, the person and settings below. */
class DrawerPanel(context: Context) : LinearLayout(context), Themed {

    val newChat = IconButton(context)
    val settings = IconButton(context)
    val chats = FrameLayout(context)
    private val header = FrameLayout(context)
    private val brand = TextView(context)
    private val placeholder = TextView(context)
    private val footer = LinearLayout(context)
    private val avatar = Avatar(context)
    private val name = TextView(context)

    init {
        orientation = VERTICAL

        brand.typeface = Fonts.medium
        brand.setText(R.string.app_name)
        header.addView(brand, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL).apply { marginStart = dp(20) })
        newChat.iconRes = R.drawable.ic_new_chat
        newChat.tone = IconButton.Tone.PRIMARY
        newChat.contentDescription = context.getString(R.string.cd_new_chat)
        header.addView(newChat, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = dp(8) })
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, dp(56)))

        placeholder.typeface = Fonts.regular
        placeholder.gravity = Gravity.CENTER
        placeholder.setText(R.string.drawer_no_chats)
        chats.addView(placeholder, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        addView(chats, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        footer.orientation = HORIZONTAL
        footer.gravity = Gravity.CENTER_VERTICAL
        footer.setPadding(dp(16), dp(8), dp(8), dp(8))
        footer.addView(avatar, LayoutParams(dp(32), dp(32)))
        name.typeface = Fonts.medium
        name.setText(R.string.you)
        footer.addView(name, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) })
        settings.iconRes = R.drawable.ic_settings
        settings.tone = IconButton.Tone.SECONDARY
        settings.contentDescription = context.getString(R.string.cd_settings)
        footer.addView(settings, LayoutParams(dp(44), dp(44)))
        addView(footer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        onThemeChanged(context.appTheme)
    }

    fun setInsets(top: Int, bottom: Int) {
        setPadding(0, top, 0, bottom)
    }

    override fun onThemeChanged(theme: Theme) {
        background = Shapes.solid(theme.surfaceElevated)
        brand.setTextColor(theme.textPrimary)
        brand.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.TITLE))
        placeholder.setTextColor(theme.textTertiary)
        placeholder.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.SECONDARY))
        name.setTextColor(theme.textPrimary)
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
    }
}
