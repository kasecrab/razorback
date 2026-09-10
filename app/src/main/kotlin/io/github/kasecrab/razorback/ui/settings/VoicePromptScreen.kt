package io.github.kasecrab.razorback.ui.settings

import android.content.Context
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.core.Keys
import io.github.kasecrab.razorback.ui.core.Screen
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.nav
import io.github.kasecrab.razorback.ui.widget.Caption
import io.github.kasecrab.razorback.ui.widget.Chip
import io.github.kasecrab.razorback.ui.widget.TextArea
import io.github.kasecrab.razorback.ui.widget.TopBar
import io.github.kasecrab.razorback.voice.VoicePrompt

/** The instructions a spoken turn adds after the system prompt; blank means the built-in text. */
class VoicePromptScreen(context: Context) : Screen(context) {

    private val prefs = App.instance.prefs
    private val bar = TopBar(context)
    private val text = TextArea(context)

    init {
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        bar.set(R.drawable.ic_arrow_back, context.getString(R.string.cd_back), context.getString(R.string.voice_prompt_row))
        bar.leading.setOnClickListener { context.nav.pop() }
        column.addView(bar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val list = LinearLayout(context)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(16), dp(8), dp(16), dp(32))
        val hint = Caption(context)
        hint.setText(R.string.voice_prompt_hint)
        hint.setPadding(0, 0, 0, dp(12))
        list.addView(hint)
        text.setHint(R.string.voice_prompt_row)
        text.text = prefs[Keys.VOICE_PROMPT].ifEmpty { VoicePrompt.DEFAULT }
        text.edit.minLines = 10
        text.onTextChanged = { prefs[Keys.VOICE_PROMPT] = if (it.trim() == VoicePrompt.DEFAULT) "" else it }
        list.addView(text, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val reset = Chip(context)
        reset.style = Chip.Style.OUTLINE
        reset.leadingIcon = R.drawable.ic_refresh
        reset.setText(R.string.voice_prompt_reset)
        reset.setOnClickListener {
            prefs[Keys.VOICE_PROMPT] = ""
            text.text = VoicePrompt.DEFAULT
        }
        list.addView(reset, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(36)).apply { topMargin = dp(12) })
        val scroll = ScrollView(context)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        column.addView(scroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
}
