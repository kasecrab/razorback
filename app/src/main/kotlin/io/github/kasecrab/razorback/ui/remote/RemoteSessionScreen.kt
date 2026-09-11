package io.github.kasecrab.razorback.ui.remote

import android.content.Context
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.remote.Frames
import io.github.kasecrab.razorback.remote.RemoteLink
import io.github.kasecrab.razorback.ui.core.Screen
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.nav
import io.github.kasecrab.razorback.ui.md.MessageView
import io.github.kasecrab.razorback.ui.widget.ActionSheet
import io.github.kasecrab.razorback.ui.widget.TextField
import io.github.kasecrab.razorback.ui.widget.TopBar
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * A session running somewhere else.
 *
 * The transcript is the same markdown renderer the local chat uses, so a
 * reply streaming in from a machine across the room redraws the way one from
 * the model does: only the last paragraph, only when it changes.
 */
class RemoteSessionScreen(context: Context, private val session: String) :
    Screen(context), RemoteLink.Watcher {

    private val link = App.instance.remote
    private val bar = TopBar(context)
    private val scroll = ScrollView(context)
    private val body = MessageView(context)
    private val input = TextField(context)
    private val text = StringBuilder()
    private var question: Frames.Question? = null

    init {
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL

        bar.set(R.drawable.ic_arrow_back, context.getString(R.string.cd_back), "session")
        bar.leading.setOnClickListener { context.nav.pop() }
        column.addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        scroll.addView(body)
        scroll.isFillViewport = true
        column.addView(
            scroll,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
                setMargins(dp(16), dp(8), dp(16), 0)
            },
        )

        input.setHint(R.string.composer_hint)
        input.edit.imeOptions = EditorInfo.IME_ACTION_SEND
        input.edit.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEND) {
                say()
                true
            } else {
                false
            }
        }
        column.addView(
            input,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(12), dp(8), dp(12), dp(12))
            },
        )

        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onEnter() {
        link.add(this)
        link.attach(session)
        bar.set(R.drawable.ic_arrow_back, context.getString(R.string.cd_back), title())
        // What was kept from last time, before anything new arrives.
        context.let {
            App.instance.scope.launch {
                val kept = App.instance.remoteStore.events(session)
                if (text.isEmpty()) onEvents(session, kept)
            }
        }
    }

    override fun onExit() {
        link.remove(this)
    }

    private fun title(): String =
        link.sessions.firstOrNull { it.id == session }?.let { it.name ?: it.title }
            ?: session.take(8)

    private fun say() {
        val said = input.text.trim()
        if (said.isEmpty()) return
        if (!link.ready()) {
            trouble("that machine is not connected")
            return
        }
        input.text = ""
        append("\n\n**you:** $said\n\n")
        link.submit(said)
    }

    // ---- what the machine says ------------------------------------------

    override fun onEvents(session: String, events: List<JSONObject>) {
        if (session != this.session && session.isNotEmpty()) return
        for (event in events) {
            Frames.eventText(event)?.let { append(it) }
            Frames.eventTool(event)?.let { append("\n\n`$it`\n\n") }
        }
    }

    /**
     * What was said before this phone was listening. Messages, not events —
     * they carry a role and their whole text rather than a piece of one.
     */
    override fun onSnapshot(session: String, messages: List<JSONObject>) {
        if (session != this.session && session.isNotEmpty()) return
        if (text.isNotEmpty()) return
        val out = StringBuilder()
        for (m in messages) {
            val said = m.optString("content")
            if (said.isBlank()) continue
            when (m.optString("role")) {
                "user" -> out.append("\n\n**you:** ").append(said).append("\n\n")
                "assistant" -> out.append(said)
                else -> {}
            }
        }
        if (out.isNotEmpty()) append(out.toString())
    }

    override fun onAsk(question: Frames.Question, isTool: Boolean) {
        this.question = question
        val what = if (isTool) "Run ${question.what}?" else question.what
        ActionSheet(context)
            .header(what, question.reason.ifBlank { "asked by the machine" })
            .add(R.drawable.ic_check, "Allow") { answer(true) }
            .add(R.drawable.ic_close, "Deny", danger = true) { answer(false) }
            .show()
    }

    override fun onAnswered(id: Long, by: String) {
        if (question?.id == id) question = null
        append("\n\n_answered by " + by + "_\n\n")
    }

    override fun onState(state: Frames.SessionState) {
        if (state.session != session) return
        bar.setSubtitle(if (state.busy) "working" else state.model)
    }

    override fun onTrouble(text: String) = trouble(text)

    private fun answer(allow: Boolean) {
        val asked = question ?: return
        question = null
        link.answerTool(asked.id, allow)
    }

    private fun trouble(what: String) {
        append("\n\n_${what}_\n\n")
    }

    private fun append(more: String) {
        text.append(more)
        body.render(text.toString())
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
