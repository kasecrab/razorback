package io.github.kasecrab.razorback.ui.chat

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.core.Fmt
import io.github.kasecrab.razorback.model.Message
import io.github.kasecrab.razorback.model.MessageStatus
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.icon
import io.github.kasecrab.razorback.ui.widget.Shapes

/**
 * The model's reasoning, folded under a one-line header. Open while the model is still
 * thinking, folded once the answer starts, and toggled by tap after that.
 */
class ThinkingView(context: Context) : LinearLayout(context), Themed {

    private val header = LinearLayout(context)
    private val icon = ImageView(context)
    private val label = TextView(context)
    private val chevron = ImageView(context)
    private val body = TextView(context)
    private var expanded = false
    private var userToggled = false
    private var boundId: String? = null

    init {
        orientation = VERTICAL
        header.orientation = HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.isClickable = true
        header.setPadding(dp(10), dp(6), dp(10), dp(6))
        icon.scaleType = ImageView.ScaleType.CENTER
        header.addView(icon, LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) })
        label.typeface = Fonts.medium
        header.addView(label, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        chevron.scaleType = ImageView.ScaleType.CENTER
        header.addView(chevron, LayoutParams(dp(20), dp(20)))
        addView(header, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        body.typeface = Fonts.regular
        body.setPadding(dp(12), dp(4), dp(12), dp(8))
        body.setTextIsSelectable(true)
        body.visibility = View.GONE
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        header.setOnClickListener {
            userToggled = true
            setExpanded(!expanded)
        }
        onThemeChanged(context.appTheme)
    }

    fun bind(m: Message) {
        val reasoning = m.reasoning
        if (reasoning.isNullOrEmpty()) {
            visibility = View.GONE
            return
        }
        visibility = View.VISIBLE
        if (boundId != m.id) {
            boundId = m.id
            userToggled = false
        }
        val thinkingNow = m.status == MessageStatus.STREAMING && m.content.isEmpty()
        label.text = when {
            thinkingNow -> context.getString(R.string.thinking_live)
            m.reasoningEndedAt != null -> context.getString(R.string.thought_for, Fmt.duration(m.reasoningEndedAt!! - (m.firstTokenAt ?: m.createdAt)))
            else -> context.getString(R.string.thought)
        }
        body.text = reasoning
        if (!userToggled) setExpanded(thinkingNow)
    }

    private fun setExpanded(value: Boolean) {
        expanded = value
        body.visibility = if (value) View.VISIBLE else View.GONE
        chevron.rotation = if (value) 180f else 0f
    }

    override fun onThemeChanged(theme: Theme) {
        header.background = Shapes.ripple(theme.accentSoft, Shapes.pill(theme.surface), 999f)
        icon.setImageDrawable(context.icon(R.drawable.ic_brain, theme.textSecondary))
        chevron.setImageDrawable(context.icon(R.drawable.ic_chevron_down, theme.textTertiary))
        label.setTextColor(theme.textSecondary)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.SECONDARY))
        body.setTextColor(theme.textSecondary)
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.SECONDARY))
        body.setLineSpacing(0f, 1.15f)
    }
}
