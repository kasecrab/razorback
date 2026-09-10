package io.github.kasecrab.razorback.ui.chat

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Screen
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.widget.IconButton

class ChatScreen(context: Context) : Screen(context) {

    private val column = LinearLayout(context)
    private val topBar = FrameLayout(context)
    private val menu = IconButton(context)
    private val title = TextView(context)
    private val newChat = IconButton(context)
    private val content = FrameLayout(context)
    private val empty = EmptyState(context)
    val composer = Composer(context)

    init {
        column.orientation = LinearLayout.VERTICAL
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        menu.iconRes = R.drawable.ic_menu
        menu.tone = IconButton.Tone.PRIMARY
        menu.contentDescription = context.getString(R.string.cd_menu)
        topBar.addView(menu, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.START or Gravity.CENTER_VERTICAL).apply { marginStart = dp(6) })

        title.typeface = Fonts.medium
        title.text = context.getString(R.string.app_name)
        title.gravity = Gravity.CENTER
        topBar.addView(title, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        newChat.iconRes = R.drawable.ic_new_chat
        newChat.tone = IconButton.Tone.PRIMARY
        newChat.contentDescription = context.getString(R.string.cd_new_chat)
        topBar.addView(newChat, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = dp(6) })
        column.addView(topBar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(56)))

        content.addView(empty, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        column.addView(content, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        column.addView(
            composer,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(10), dp(4), dp(10), dp(8))
            },
        )
    }

    override fun onThemeChanged(theme: Theme) {
        super.onThemeChanged(theme)
        title.setTextColor(theme.textPrimary)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
    }
}
