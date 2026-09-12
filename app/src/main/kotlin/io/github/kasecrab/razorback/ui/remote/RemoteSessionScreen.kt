package io.github.kasecrab.razorback.ui.remote

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.model.Message
import io.github.kasecrab.razorback.model.MessageStatus
import io.github.kasecrab.razorback.model.Role
import io.github.kasecrab.razorback.model.ToolCall
import io.github.kasecrab.razorback.remote.Frames
import io.github.kasecrab.razorback.remote.RemoteLink
import io.github.kasecrab.razorback.ui.chat.ChatAdapter
import io.github.kasecrab.razorback.ui.chat.Composer
import io.github.kasecrab.razorback.ui.core.Haptics
import io.github.kasecrab.razorback.ui.core.Screen
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.nav
import io.github.kasecrab.razorback.ui.widget.ActionSheet
import io.github.kasecrab.razorback.ui.widget.IconButton
import io.github.kasecrab.razorback.ui.widget.InputSheet
import io.github.kasecrab.razorback.ui.widget.TopBar
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * A session running somewhere else, drawn exactly like one running here: the same
 * message list, the same composer, the same tool cards. What arrives over the link is
 * turned into the app's own [Message]s, so a reply from a machine across the room gets
 * the markdown, the code blocks and the folded reasoning the local chat has.
 */
class RemoteSessionScreen(context: Context, private val session: String, private val cwdHint: String? = null) : Screen(context), RemoteLink.Watcher {

    private val app = App.instance
    private val link = app.remote
    private val column = LinearLayout(context)
    private val bar = TopBar(context)
    private val content = FrameLayout(context)
    private val list = RecyclerView(context)
    private val messages = ArrayList<Message>()
    private val adapter = ChatAdapter(messages)
    private val layout = LinearLayoutManager(context).apply { stackFromEnd = true }
    private val pill = IconButton(context)
    private val composer = Composer(context)
    private var question: Frames.Question? = null
    /** Whole messages have come from the machine; what was kept here is no longer the best copy. */
    private var snapshotted = false
    private var attached = false
    private var stateSeen = false
    private var busy = false
    private var next = 0
    private var following = true
        set(value) {
            if (field == value) return
            field = value
            pill.animate().alpha(if (value) 0f else 1f).setDuration(context.appTheme.durShort).start()
            pill.isClickable = !value
        }

    init {
        column.orientation = LinearLayout.VERTICAL
        bar.set(R.drawable.ic_arrow_back, context.getString(R.string.cd_back), title())
        bar.leading.setOnClickListener { context.nav.pop() }
        column.addView(bar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        list.layoutManager = layout
        list.adapter = adapter
        list.itemAnimator = null
        list.clipToPadding = false
        list.setPadding(0, dp(8), 0, dp(8))
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy < 0 && rv.scrollState == RecyclerView.SCROLL_STATE_DRAGGING) following = false
                if (!rv.canScrollVertically(1)) following = true
            }
        })
        content.addView(list, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        pill.iconRes = R.drawable.ic_chevron_down
        pill.filled = true
        pill.tone = IconButton.Tone.PRIMARY
        pill.contentDescription = context.getString(R.string.cd_scroll_bottom)
        pill.alpha = 0f
        pill.isClickable = false
        pill.setOnClickListener {
            list.smoothScrollToPosition(adapter.itemCount)
            following = true
        }
        content.addView(pill, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.END or Gravity.BOTTOM).apply { setMargins(0, 0, dp(16), dp(12)) })
        column.addView(content, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        composer.plain()
        composer.onSend = { text, _ -> say(text) }
        composer.onStop = { link.interrupt() }
        column.addView(
            composer,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { setMargins(dp(10), dp(4), dp(10), dp(8)) },
        )
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        subtitle()
    }

    override fun onEnter() {
        link.add(this)
        val known = link.sessions.firstOrNull { it.id == session }
        if (known != null && !known.live) {
            // On disk, not running: the machine is asked to bring it back, and the attach
            // follows once the next session list shows it live.
            bar.setSubtitle(context.getString(R.string.remote_starting))
            link.resume(session)
        } else {
            attach()
        }
        app.scope.launch {
            val kept = app.remoteStore.events(session)
            // Only until the machine sends the real thing: kept events hold what the
            // model said and nothing the person did.
            if (messages.isEmpty() && !snapshotted) onEvents(session, kept)
        }
        val asked = link.pending
        if (asked != null && asked.session == session) onAsk(asked, link.pendingIsTool)
    }

    override fun onPause() {
        composer.dictation.stop()
    }

    override fun onExit() {
        composer.dictation.release()
        link.detach()
        link.remove(this)
    }

    override fun onInsetsChanged(top: Int, bottom: Int, left: Int, right: Int) {
        column.setPadding(left, top, right, bottom)
    }

    override fun onThemeChanged(theme: Theme) {
        super.onThemeChanged(theme)
        column.setBackgroundColor(theme.bg)
    }

    private fun attach() {
        attached = true
        link.attach(session)
    }

    private fun title(): String =
        link.sessions.firstOrNull { it.id == session }?.let { (it.name ?: it.title).ifBlank { it.cwd.substringAfterLast('/') } }
            ?: cwdHint?.substringAfterLast('/')?.ifBlank { null }
            ?: context.getString(R.string.remote_new_session)

    /** Where it runs, on which machine, and whether it is working; "connecting" until the machine has answered the attach. */
    private fun subtitle() {
        val host = link.machine?.host ?: context.getString(R.string.remote)
        val cwd = link.sessions.firstOrNull { it.id == session }?.cwd?.ifBlank { null } ?: cwdHint
        val state = when {
            !link.connected -> context.getString(R.string.remote_offline)
            !stateSeen -> context.getString(R.string.remote_connecting)
            busy -> context.getString(R.string.remote_working)
            else -> null
        }
        bar.setSubtitle(listOfNotNull(host, cwd, state).joinToString("  ·  "))
    }

    private fun say(text: String) {
        if (!link.connected) {
            Haptics.reject()
            Toast.makeText(context, R.string.remote_offline, Toast.LENGTH_SHORT).show()
            composer.input.setText(text)
            return
        }
        close()
        add(Message(id = id(), role = Role.USER, content = text))
        link.submit(text)
    }

    // ---- what the machine says ------------------------------------------

    override fun onSessions(sessions: List<Frames.Session>) {
        bar.title.text = title()
        subtitle()
        val mine = sessions.firstOrNull { it.id == session } ?: return
        if (mine.live && !attached) attach()
    }

    override fun onLink() {
        // Back after a drop, the link repeats the attach itself; the machine's answer sets the state again.
        if (!link.connected) stateSeen = false
        subtitle()
    }

    override fun onEvents(session: String, events: List<JSONObject>) {
        if (session != this.session && session.isNotEmpty()) return
        for (event in events) {
            when (event.optString("type")) {
                "text" -> stream(event.optString("text"), reasoning = false)
                "reasoning" -> stream(event.optString("text"), reasoning = true)
                "tool_start" -> {
                    close()
                    val call = event.optJSONObject("call")
                    val name = call?.optJSONObject("function")?.optString("name").orEmpty().ifBlank { "tool" }
                    val args = call?.optJSONObject("function")?.optString("arguments").orEmpty()
                    add(Message(id = id(), role = Role.ASSISTANT, toolCalls = listOf(ToolCall(call?.optString("id").orEmpty(), name, args))))
                }
                "tool_end", "tool_denied" -> {
                    val name = event.optJSONObject("call")?.optJSONObject("function")?.optString("name").orEmpty().ifBlank { "tool" }
                    val result = event.optJSONObject("result")
                    val denied = event.optString("type") == "tool_denied"
                    val output = if (denied) event.optString("reason").ifBlank { context.getString(R.string.tool_failed, name) } else result?.optString("output").orEmpty()
                    val failed = denied || result?.optBoolean("is_error") == true
                    add(Message(id = id(), role = Role.TOOL, content = output, status = if (failed) MessageStatus.ERROR else MessageStatus.COMPLETE, toolName = name))
                }
                "error" -> {
                    val last = streaming()
                    if (last != null) {
                        last.status = MessageStatus.ERROR
                        last.error = event.optString("error")
                        adapter.notifyItemChanged(messages.size - 1)
                    } else {
                        add(Message(id = id(), role = Role.ASSISTANT, status = MessageStatus.ERROR, error = event.optString("error")))
                    }
                }
                "notice" -> note(event.optString("text"))
                "turn_end" -> close()
            }
        }
    }

    /** Scrollback, from before this phone was listening: whole messages, so they go in as they are. */
    override fun onSnapshot(session: String, snapshot: List<JSONObject>) {
        if (session != this.session && session.isNotEmpty()) return
        snapshotted = true
        messages.clear()
        for (m in snapshot) {
            val said = m.optString("content")
            val role = when (m.optString("role")) {
                "user" -> Role.USER
                "assistant" -> Role.ASSISTANT
                else -> continue
            }
            if (said.isBlank() && role == Role.ASSISTANT) continue
            val reasoning = m.optString("reasoning").ifBlank { null }
            messages.add(Message(id = id(), role = role, content = said, reasoning = reasoning))
        }
        adapter.notifyDataSetChanged()
        follow()
    }

    override fun onAsk(question: Frames.Question, isTool: Boolean) {
        if (question.session != session) return
        this.question = question
        if (isTool) {
            ActionSheet(context)
                .header("Run ${question.what}?", question.reason.ifBlank { "asked by the machine" })
                .add(R.drawable.ic_check, "Allow") { answer(true) }
                .add(R.drawable.ic_close, "Deny", danger = true) { answer(false) }
                .show()
            return
        }
        // A question: its choices, a typed answer, or nothing. An ask with several
        // questions is answered at the keyboard; from here it can only be dismissed.
        val sheet = ActionSheet(context).header(question.header.ifBlank { "The machine asks" }, question.what)
        if (question.count == 1) {
            for (option in question.options) {
                sheet.add(R.drawable.ic_check, option) { reply(question, option, "") }
            }
            sheet.add(R.drawable.ic_edit, "Type an answer") {
                InputSheet(context, question.what, "") { typed -> reply(question, null, typed) }.show()
            }
        } else {
            sheet.add(R.drawable.ic_edit, "${question.count} questions: answer at the machine") {}
        }
        sheet.add(R.drawable.ic_close, "Dismiss", danger = true) {
            if (this.question?.id == question.id) this.question = null
            link.dismissAsk(question.id)
        }
        sheet.show()
    }

    private fun reply(asked: Frames.Question, picked: String?, note: String) {
        if (question?.id == asked.id) question = null
        link.answerAsk(asked.id, picked, note)
    }

    override fun onAnswered(id: Long, by: String) {
        if (question?.id == id) question = null
        note("answered by $by")
    }

    override fun onState(state: Frames.SessionState) {
        if (state.session != session) return
        stateSeen = true
        busy = state.busy
        composer.streaming = busy
        if (!busy) close()
        subtitle()
    }

    override fun onTrouble(text: String) {
        // Attached before the machine had the session on its books: the next list that shows it running tries again.
        if (text.contains("no such session")) attached = false
        note(text)
    }

    private fun answer(allow: Boolean) {
        val asked = question ?: return
        question = null
        link.answerTool(asked.id, allow)
    }

    // ---- keeping the list in step ---------------------------------------

    private fun streaming(): Message? = messages.lastOrNull()?.takeIf { it.role == Role.ASSISTANT && it.status == MessageStatus.STREAMING }

    /** Text or reasoning into the reply being written, starting one if there is none. */
    private fun stream(more: String, reasoning: Boolean) {
        if (more.isEmpty()) return
        val last = streaming()
        if (last != null) {
            if (reasoning) last.reasoning = (last.reasoning ?: "") + more else last.content += more
            if (!reasoning && last.reasoningEndedAt == null && !last.reasoning.isNullOrEmpty()) last.reasoningEndedAt = System.currentTimeMillis()
            adapter.notifyItemChanged(messages.size - 1, ChatAdapter.STREAM)
            if (following) list.scrollToPosition(messages.size - 1)
            Haptics.stream()
            return
        }
        add(
            Message(
                id = id(),
                role = Role.ASSISTANT,
                content = if (reasoning) "" else more,
                reasoning = if (reasoning) more else null,
                status = MessageStatus.STREAMING,
            ),
        )
    }

    /** The reply is finished, so it stops being one that is still arriving. */
    private fun close() {
        val last = streaming() ?: return
        last.status = MessageStatus.COMPLETE
        last.finishedAt = System.currentTimeMillis()
        adapter.notifyItemChanged(messages.size - 1)
    }

    private fun note(what: String) {
        if (what.isBlank()) return
        close()
        add(Message(id = id(), role = Role.ASSISTANT, content = "_${what}_"))
    }

    private fun add(message: Message) {
        messages.add(message)
        adapter.notifyItemInserted(messages.size - 1)
        following = true
        follow()
    }

    private fun follow() {
        if (following) list.scrollToPosition(adapter.itemCount - 1)
    }

    private fun id(): String = "r${next++}"
}
