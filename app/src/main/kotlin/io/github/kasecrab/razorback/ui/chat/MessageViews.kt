package io.github.kasecrab.razorback.ui.chat

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
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
import io.github.kasecrab.razorback.ui.md.MessageView
import io.github.kasecrab.razorback.ui.widget.IconButton
import io.github.kasecrab.razorback.ui.widget.Shapes
import java.text.DateFormat
import java.util.Date

/** The person's turn: a bubble hugging the end edge; long-press for actions. */
class UserMessageView(context: Context) : LinearLayout(context), Themed {

    private val images = ImageGridView(context)
    private val files = LinearLayout(context)
    private val bubble = TextView(context)
    var onMenu: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.END
        setPadding(dp(56), dp(6), dp(16), dp(6))
        addView(images, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) })
        files.orientation = VERTICAL
        files.gravity = Gravity.END
        addView(files, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        bubble.typeface = Fonts.regular
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10))
        bubble.setOnLongClickListener {
            onMenu?.invoke()
            true
        }
        addView(bubble, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        onThemeChanged(context.appTheme)
    }

    fun bind(m: Message) {
        images.set(m.images.filter { !it.startsWith("data:") })
        files.removeAllViews()
        val parts = FileBlocks.split(m.content)
        for (name in parts.files) {
            val chip = io.github.kasecrab.razorback.ui.widget.Chip(context)
            chip.style = io.github.kasecrab.razorback.ui.widget.Chip.Style.SOFT
            chip.leadingIcon = R.drawable.ic_file
            chip.text = name
            files.addView(chip, LayoutParams(LayoutParams.WRAP_CONTENT, dp(36)).apply { bottomMargin = dp(6) })
        }
        bubble.text = parts.text
        bubble.visibility = if (parts.text.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onThemeChanged(theme: Theme) {
        bubble.background = Shapes.rounded(theme.userBubble, dp(theme.radiusL))
        bubble.setTextColor(theme.textPrimary)
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
    }
}

/**
 * The model's turn: model name and time on top, reasoning folded beneath, the answer as
 * markdown, a status line if it was cut short, and usage on tap of the header.
 */
class AssistantMessageView(context: Context) : LinearLayout(context), Themed {

    private val header = LinearLayout(context)
    private val who = TextView(context)
    private val more = IconButton(context)
    private val thinking = ThinkingView(context)
    private val body = MessageView(context)
    private val pictures = ImageGridView(context)
    private val note = TextView(context)
    private val dots = WaitingDots(context)
    private val usage = TextView(context)
    private var message: Message? = null
    var onMenu: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        setPadding(dp(16), dp(8), dp(8), dp(6))

        header.orientation = HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        who.typeface = Fonts.medium
        who.maxLines = 1
        header.addView(who, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        more.iconRes = R.drawable.ic_more
        more.contentDescription = context.getString(R.string.cd_more)
        more.setOnClickListener { onMenu?.invoke() }
        header.addView(more, LayoutParams(dp(36), dp(36)))
        header.setOnClickListener { usage.visibility = if (usage.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        usage.typeface = Fonts.regular
        usage.visibility = View.GONE
        addView(usage, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) })

        addView(thinking, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(6)
            marginEnd = dp(8)
        })
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
        addView(pictures, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
            marginEnd = dp(8)
        })
        note.typeface = Fonts.regular
        note.visibility = View.GONE
        addView(note, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        dots.visibility = View.GONE
        addView(dots, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
        onThemeChanged(context.appTheme)
    }

    fun bind(m: Message) {
        val theme = context.appTheme
        message = m
        who.text = (m.model?.substringAfter('/')?.substringBefore(':') ?: "") + "  ·  " + TIME.format(Date(m.createdAt))
        thinking.bind(m)
        body.render(m.content)
        pictures.set(m.images.filter { !it.startsWith("data:") })
        val waiting = m.status == MessageStatus.STREAMING && m.content.isEmpty() && m.reasoning.isNullOrEmpty()
        val noteText = when (m.status) {
            MessageStatus.ERROR -> m.error ?: context.getString(R.string.went_wrong)
            MessageStatus.CUT -> m.error ?: context.getString(R.string.cut_short)
            MessageStatus.STREAMING, MessageStatus.COMPLETE -> null
        }
        note.text = noteText
        note.visibility = if (noteText == null) View.GONE else View.VISIBLE
        dots.visibility = if (waiting) View.VISIBLE else View.GONE
        note.setTextColor(if (m.status == MessageStatus.ERROR) theme.danger else theme.textTertiary)
        usage.text = usageLine(m)
        if (m.status == MessageStatus.STREAMING) usage.visibility = View.GONE
    }

    /** Streaming path: reasoning and body text only. */
    fun bindStream(m: Message) {
        thinking.bind(m)
        body.render(m.content)
        if (dots.visibility == View.VISIBLE && (m.content.isNotEmpty() || !m.reasoning.isNullOrEmpty())) dots.visibility = View.GONE
    }

    private fun usageLine(m: Message): String {
        val u = m.usage ?: return context.getString(R.string.cut_short).let { "" }
        val sb = StringBuilder()
        sb.append(Fmt.tokens(u.promptTokens.toLong())).append(" in · ").append(Fmt.tokens(u.completionTokens.toLong())).append(" out")
        if (u.reasoningTokens > 0) sb.append(" · ").append(Fmt.tokens(u.reasoningTokens.toLong())).append(" thinking")
        if (u.cachedTokens > 0) sb.append(" · ").append(Fmt.tokens(u.cachedTokens.toLong())).append(" cached")
        if (u.cost > 0) sb.append(" · ").append(Fmt.money(u.cost))
        val ttft = m.firstTokenAt?.let { it - m.createdAt }
        if (ttft != null && ttft > 0) sb.append(" · ").append(Fmt.duration(ttft)).append(" to first token")
        return sb.toString()
    }

    override fun onThemeChanged(theme: Theme) {
        who.setTextColor(theme.textTertiary)
        who.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.CAPTION))
        usage.setTextColor(theme.textTertiary)
        usage.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.CAPTION))
        more.onThemeChanged(theme)
        thinking.onThemeChanged(theme)
        body.onThemeChanged(theme)
        pictures.onThemeChanged(theme)
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.SECONDARY))
        dots.onThemeChanged(theme)
    }

    private companion object {
        val TIME: DateFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
    }
}

/** `<file name="x">…</file>` blocks inside a user message, shown as chips instead of raw text. */
object FileBlocks {
    class Parts(val text: String, val files: List<String>)

    private val BLOCK = Regex("<file name=\"([^\"]*)\">\n?[\\s\\S]*?\n?</file>\n?")

    fun split(content: String): Parts {
        val names = ArrayList<String>(1)
        val text = BLOCK.replace(content) {
            names.add(it.groupValues[1])
            ""
        }.trim()
        return Parts(text, names)
    }
}
