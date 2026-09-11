package io.github.kasecrab.razorback.ui.chat

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.core.str
import io.github.kasecrab.razorback.model.Message
import io.github.kasecrab.razorback.model.MessageStatus
import io.github.kasecrab.razorback.model.Role
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.icon
import io.github.kasecrab.razorback.ui.md.LinkSpan
import io.github.kasecrab.razorback.ui.widget.Shapes
import org.json.JSONException
import org.json.JSONObject

/**
 * A tool round as one card: "Searching the web for …" for the call, and the result
 * message folded beneath as a list of links.
 */
class ToolCardView(context: Context) : LinearLayout(context), Themed {

    private val header = LinearLayout(context)
    private val icon = ImageView(context)
    private val title = TextView(context)
    private val chevron = ImageView(context)
    private val body = TextView(context)
    private var expanded = false

    init {
        orientation = VERTICAL
        setPadding(dp(16), dp(4), dp(16), dp(4))
        header.orientation = HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.isClickable = true
        header.setPadding(dp(12), dp(8), dp(12), dp(8))
        icon.scaleType = ImageView.ScaleType.CENTER
        header.addView(icon, LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) })
        title.typeface = Fonts.medium
        title.maxLines = 2
        header.addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        chevron.scaleType = ImageView.ScaleType.CENTER
        header.addView(chevron, LayoutParams(dp(20), dp(20)))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        body.typeface = Fonts.regular
        body.movementMethod = LinkMovementMethod.getInstance()
        body.setPadding(dp(12), dp(6), dp(12), dp(6))
        body.visibility = View.GONE
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        header.setOnClickListener {
            io.github.kasecrab.razorback.ui.core.Haptics.tick()
            expanded = !expanded
            body.visibility = if (expanded && body.text.isNotEmpty()) View.VISIBLE else View.GONE
            chevron.rotation = if (expanded) 180f else 0f
        }
        onThemeChanged(context.appTheme)
    }

    fun bind(m: Message) {
        val theme = context.appTheme
        if (m.role == Role.ASSISTANT) {
            val call = m.toolCalls.firstOrNull()
            val query = call?.let { queryOf(it.arguments) }
            title.text = if (query != null) context.getString(R.string.searching) + ": " + query else context.getString(R.string.searching)
            body.text = ""
            chevron.visibility = View.GONE
        } else {
            val error = m.status == MessageStatus.ERROR
            val lines = m.content.lines()
            val count = lines.count { it.startsWith("http") }
            title.text = if (error) context.getString(R.string.search_failed) else context.getString(R.string.search_results, count)
            body.text = links(m.content, theme)
            chevron.visibility = View.VISIBLE
        }
        body.visibility = if (expanded && body.text.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun queryOf(arguments: String): String? = try {
        JSONObject(arguments).str("query")
    } catch (_: JSONException) {
        null
    }

    private fun links(text: String, theme: Theme): CharSequence {
        val sb = SpannableStringBuilder()
        var pendingTitle: String? = null
        for (line in text.lines()) {
            when {
                line.startsWith("http") -> {
                    val start = sb.length
                    sb.append(pendingTitle ?: line)
                    sb.setSpan(LinkSpan(line.trim(), theme.accent), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.append('\n')
                    pendingTitle = null
                }
                Regex("^\\d+\\. ").containsMatchIn(line) -> pendingTitle = line.substringAfter(". ")
            }
        }
        return sb.trimEnd()
    }

    override fun onThemeChanged(theme: Theme) {
        header.background = Shapes.ripple(theme.accentSoft, Shapes.rounded(theme.surface, dp(theme.radiusM)), dp(theme.radiusM))
        icon.setImageDrawable(context.icon(R.drawable.ic_globe, theme.textSecondary))
        chevron.setImageDrawable(context.icon(R.drawable.ic_chevron_down, theme.textTertiary))
        title.setTextColor(theme.textSecondary)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.SECONDARY))
        body.setTextColor(theme.textSecondary)
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.SECONDARY))
    }
}
