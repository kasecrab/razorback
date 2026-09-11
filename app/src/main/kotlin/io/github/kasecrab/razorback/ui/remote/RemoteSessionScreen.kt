package io.github.kasecrab.razorback.ui.remote

import android.content.Context
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.model.Message
import io.github.kasecrab.razorback.model.MessageStatus
import io.github.kasecrab.razorback.model.Role
import io.github.kasecrab.razorback.remote.Frames
import io.github.kasecrab.razorback.remote.RemoteLink
import io.github.kasecrab.razorback.ui.chat.ChatAdapter
import io.github.kasecrab.razorback.ui.core.Screen
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.nav
import io.github.kasecrab.razorback.ui.widget.ActionSheet
import io.github.kasecrab.razorback.ui.widget.TextField
import io.github.kasecrab.razorback.ui.widget.TopBar
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * A session running somewhere else, drawn by the same views as one running
 * here.
 *
 * What arrives over the link is turned into the app's own [Message]s and
 * handed to [ChatAdapter], so a reply from a machine across the room gets the
 * markdown, the code blocks and the tables the local chat already has, and
 * anything that improves there improves here without being asked to.
 */
class RemoteSessionScreen(context: Context, private val session: String) :
    Screen(context), RemoteLink.Watcher {

    private val link = App.instance.remote
    private val bar = TopBar(context)
    private val list = RecyclerView(context)
    private val messages = ArrayList<Message>()
    private val adapter = ChatAdapter(messages)
    private val input = TextField(context)
    private var question: Frames.Question? = null
    /** Whole messages have come from the machine; what was kept here is no longer the best copy. */
    private var snapshotted = false

    init {
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL

        bar.set(R.drawable.ic_arrow_back, context.getString(R.string.cd_back), title())
        bar.leading.setOnClickListener { context.nav.pop() }
        column.addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        // The same reason the local chat has none: a row that changes while
        // text streams into it must not animate every time.
        list.itemAnimator = null
        column.addView(
            list,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f),
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
        App.instance.scope.launch {
            val kept = App.instance.remoteStore.events(session)
            // Only until the machine sends the real thing: kept events hold what the
            // model said and nothing the person did.
            if (messages.isEmpty() && !snapshotted) onEvents(session, kept)
        }
        val asked = link.pending
        if (asked != null && asked.session == session) onAsk(asked, link.pendingIsTool)
    }

    override fun onExit() {
        link.detach()
        link.remove(this)
    }

    private fun title(): String =
        link.sessions.firstOrNull { it.id == session }?.let { it.name ?: it.title }
            ?: session.take(8)

    private fun say() {
        val said = input.text.trim()
        if (said.isEmpty()) return
        if (!link.ready()) {
            note("that machine is not connected")
            return
        }
        input.text = ""
        add(Message(id = "u${messages.size}", role = Role.USER, content = said))
        link.submit(said)
    }

    // ---- what the machine says ------------------------------------------

    override fun onEvents(session: String, events: List<JSONObject>) {
        if (session != this.session && session.isNotEmpty()) return
        for (event in events) {
            Frames.eventText(event)?.let { stream(it) }
            Frames.eventTool(event)?.let { close(); note("ran $it") }
            if (event.optString("type") == "turn_end") close()
        }
    }

    /**
     * Scrollback, from before this phone was listening. Whole messages rather
     * than pieces of one, so they go in as they are.
     */
    override fun onSnapshot(session: String, snapshot: List<JSONObject>) {
        if (session != this.session && session.isNotEmpty()) return
        snapshotted = true
        messages.clear()
        for (m in snapshot) {
            val said = m.optString("content")
            if (said.isBlank()) continue
            val role = when (m.optString("role")) {
                "user" -> Role.USER
                "assistant" -> Role.ASSISTANT
                else -> continue
            }
            messages.add(Message(id = "s${messages.size}", role = role, content = said))
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
                io.github.kasecrab.razorback.ui.widget.InputSheet(context, question.what, "") { typed ->
                    reply(question, null, typed)
                }.show()
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
        bar.setSubtitle(if (state.busy) "working" else state.model)
    }

    override fun onTrouble(text: String) = note(text)

    private fun answer(allow: Boolean) {
        val asked = question ?: return
        question = null
        link.answerTool(asked.id, allow)
    }

    // ---- keeping the list in step ---------------------------------------

    /** Text into the reply being written, starting one if there is none. */
    private fun stream(more: String) {
        val last = messages.lastOrNull()
        if (last != null && last.role == Role.ASSISTANT && last.status == MessageStatus.STREAMING) {
            last.content += more
            adapter.notifyItemChanged(messages.size - 1, ChatAdapter.STREAM)
            follow()
            return
        }
        add(
            Message(
                id = "a${messages.size}",
                role = Role.ASSISTANT,
                content = more,
                status = MessageStatus.STREAMING,
            ),
        )
    }

    /** The reply is finished, so it stops being one that is still arriving. */
    private fun close() {
        val last = messages.lastOrNull() ?: return
        if (last.status != MessageStatus.STREAMING) return
        last.status = MessageStatus.COMPLETE
        adapter.notifyItemChanged(messages.size - 1)
    }

    private fun note(what: String) {
        close()
        add(Message(id = "n${messages.size}", role = Role.ASSISTANT, content = "_${what}_"))
    }

    private fun add(message: Message) {
        messages.add(message)
        adapter.notifyItemInserted(messages.size - 1)
        follow()
    }

    private fun follow() {
        list.post { list.scrollToPosition(messages.size - 1) }
    }
}
