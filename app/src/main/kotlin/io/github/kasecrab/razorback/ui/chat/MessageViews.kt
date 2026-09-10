package io.github.kasecrab.razorback.ui.chat

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.kasecrab.razorback.model.Message
import io.github.kasecrab.razorback.model.MessageStatus
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.widget.Shapes

/** The person's turn: a bubble hugging the end edge. */
class UserMessageView(context: Context) : FrameLayout(context), Themed {

    private val bubble = TextView(context)

    init {
        bubble.typeface = Fonts.regular
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10))
        bubble.setTextIsSelectable(true)
        addView(
            bubble,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.END).apply {
                setMargins(dp(56), dp(6), dp(16), dp(6))
            },
        )
        onThemeChanged(context.appTheme)
    }

    fun bind(m: Message) {
        bubble.text = m.content
    }

    override fun onThemeChanged(theme: Theme) {
        bubble.background = Shapes.rounded(theme.userBubble, dp(theme.radiusL))
        bubble.setTextColor(theme.textPrimary)
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
    }
}

/** The model's turn: flat, full width, with a status line when something went wrong. */
class AssistantMessageView(context: Context) : LinearLayout(context), Themed {

    private val body = TextView(context)
    private val note = TextView(context)

    init {
        orientation = VERTICAL
        setPadding(dp(16), dp(6), dp(16), dp(6))
        body.typeface = Fonts.regular
        body.setTextIsSelectable(true)
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        note.typeface = Fonts.regular
        note.visibility = View.GONE
        addView(note, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        onThemeChanged(context.appTheme)
    }

    fun bind(m: Message) {
        val theme = context.appTheme
        body.text = m.content
        val noteText = when (m.status) {
            MessageStatus.ERROR -> m.error ?: "Something went wrong"
            MessageStatus.CUT -> m.error ?: "Cut short"
            MessageStatus.STREAMING -> if (m.content.isEmpty()) "…" else null
            MessageStatus.COMPLETE -> null
        }
        note.text = noteText
        note.visibility = if (noteText == null) View.GONE else View.VISIBLE
        note.setTextColor(if (m.status == MessageStatus.ERROR) theme.danger else theme.textTertiary)
    }

    /** Streaming path: only the text changes, nothing is re-measured but the body. */
    fun bindStream(m: Message) {
        body.text = m.content
        if (m.content.isNotEmpty() && note.visibility == View.VISIBLE && m.status == MessageStatus.STREAMING) {
            note.visibility = View.GONE
        }
    }

    override fun onThemeChanged(theme: Theme) {
        body.setTextColor(theme.textPrimary)
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.SECONDARY))
    }
}
