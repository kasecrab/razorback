package io.github.kasecrab.razorback.remote

import org.json.JSONArray
import org.json.JSONObject

/**
 * The wire format: what the relay sees, and what is sealed inside it.
 *
 * Written against the same shapes as `crates/ah-remote-proto`, which is the
 * one place they are defined. Anything here that drifts from that is a link
 * that connects and then makes no sense, so [FramesTest] checks the pieces
 * that would drift quietly.
 */
object Frames {

    const val PROTO = 1

    /** What the relay reads. Everything else is inside `ct`, sealed. */
    sealed class Envelope {
        /** One sealed payload from the desktop, with the relay's numbering. */
        data class Evt(val link: String, val seq: Long, val ct: String, val n: Long) : Envelope()

        /** What the relay has to say about the connection itself. */
        data class Ctl(val error: String, val serverMs: Long?) : Envelope()

        /** What was asked for is older than what is kept. */
        data class Gap(val from: Long) : Envelope()

        /** Something this build has no idea about. */
        data object Unknown : Envelope()
    }

    fun readEnvelope(text: String): Envelope? {
        val o = try {
            JSONObject(text)
        } catch (_: org.json.JSONException) {
            return null
        }
        if (o.optInt("v", -1) != PROTO) return null
        return when (o.optString("t")) {
            "evt" -> Envelope.Evt(
                link = o.optString("link"),
                seq = o.optLong("seq"),
                ct = o.optString("ct"),
                n = o.optLong("n"),
            )
            "ctl" -> Envelope.Ctl(
                error = o.optString("e"),
                serverMs = if (o.has("server_ms")) o.optLong("server_ms") else null,
            )
            "gap" -> Envelope.Gap(o.optLong("from"))
            else -> Envelope.Unknown
        }
    }

    /** Ask the relay for everything after a point. */
    fun subscribe(since: Long, max: Int): String = JSONObject()
        .put("t", "sub").put("v", PROTO).put("since", since).put("max", max)
        .toString()

    /** One sealed payload on its way to the desktop. */
    fun command(link: String, plink: String, seq: Long, ct: String): String = JSONObject()
        .put("t", "cmd").put("v", PROTO).put("link", link).put("plink", plink)
        .put("seq", seq).put("ct", ct)
        .toString()

    // ---- what the desktop says, once a frame is open --------------------

    /** [roots] are the tops of the directory trees a session may be started in; empty for a window, which starts none. */
    data class Machine(val host: String, val os: String, val version: String, val holder: String, val roots: List<String> = emptyList())

    data class Session(
        val id: String,
        val name: String?,
        val title: String,
        val cwd: String,
        val model: String,
        val startedMs: Long,
        val messages: Int,
        val live: Boolean,
    )

    data class SessionState(val session: String, val busy: Boolean, val model: String, val cwd: String)

    /**
     * Something the machine is waiting on. For a tool, [what] names the tool and [reason]
     * says why it asked. For a question, [what] is the first question, [options] its
     * choices, and [count] how many questions the ask holds: only a lone one can be
     * answered from here, the rest wait for the keyboard.
     */
    data class Question(
        val session: String,
        val id: Long,
        val what: String,
        val reason: String,
        val header: String = "",
        val options: List<String> = emptyList(),
        val count: Int = 1,
    )

    sealed class FromDesk {
        data class Hello(val machine: Machine) : FromDesk()
        data class Sessions(val list: List<Session>) : FromDesk()
        data class State(val state: SessionState) : FromDesk()
        data class Snapshot(val session: String, val messages: List<JSONObject>, val truncated: Boolean) : FromDesk()
        data class Events(val session: String, val events: List<JSONObject>) : FromDesk()
        data class Ask(val question: Question, val isTool: Boolean) : FromDesk()
        data class Answered(val session: String, val id: Long, val by: String) : FromDesk()
        /** [session] is the id a new or resumed session ended up with, present only when one was started. */
        data class Ack(val ok: Boolean, val error: String?, val session: String? = null) : FromDesk()
        data class Notice(val text: String) : FromDesk()
        data class Blob(val id: String, val mime: String, val seq: Int, val last: Boolean, val b64: String) : FromDesk()
        data class Bye(val reason: String) : FromDesk()
        data object Unknown : FromDesk()
    }

    fun readPayload(plain: ByteArray): FromDesk? {
        val o = try {
            JSONObject(String(plain, Charsets.UTF_8))
        } catch (_: org.json.JSONException) {
            return null
        }
        return when (o.optString("k")) {
            "hello" -> FromDesk.Hello(
                Machine(
                    host = o.optString("host"),
                    os = o.optString("os"),
                    version = o.optString("ah_version"),
                    holder = o.optString("holder"),
                    roots = strings(o.optJSONArray("roots")),
                ),
            )
            "sessions" -> FromDesk.Sessions(readSessions(o.optJSONArray("list")))
            "state" -> FromDesk.State(
                SessionState(
                    session = o.optString("session"),
                    busy = o.optBoolean("busy"),
                    model = o.optString("model"),
                    cwd = o.optString("cwd"),
                ),
            )
            "snapshot" -> FromDesk.Snapshot(
                session = o.optString("session"),
                messages = objects(o.optJSONArray("messages")),
                truncated = o.optBoolean("truncated"),
            )
            "events" -> FromDesk.Events(o.optString("session"), objects(o.optJSONArray("evs")))
            "ask_permission" -> FromDesk.Ask(
                Question(
                    session = o.optString("session"),
                    id = o.optLong("id"),
                    what = o.optJSONObject("call")?.optJSONObject("function")?.optString("name")
                        ?: "a tool",
                    reason = o.optString("reason"),
                ),
                isTool = true,
            )
            "ask_user" -> {
                val ask = o.optJSONObject("ask")
                val questions = ask?.optJSONArray("questions")
                val first = questions?.optJSONObject(0)
                FromDesk.Ask(
                    Question(
                        session = o.optString("session"),
                        id = o.optLong("id"),
                        what = first?.optString("question").orEmpty().ifBlank { "a question" },
                        reason = "",
                        header = first?.optString("header").orEmpty(),
                        options = labels(first?.optJSONArray("options")),
                        count = questions?.length() ?: 1,
                    ),
                    isTool = false,
                )
            }
            "answered" -> FromDesk.Answered(o.optString("session"), o.optLong("id"), o.optString("by"))
            "ack" -> FromDesk.Ack(
                o.optBoolean("ok"),
                if (o.isNull("error")) null else o.optString("error"),
                if (o.isNull("session")) null else o.optString("session").ifBlank { null },
            )
            "notice" -> FromDesk.Notice(o.optString("text"))
            "blob" -> FromDesk.Blob(
                o.optString("id"), o.optString("mime"), o.optInt("seq"),
                o.optBoolean("last"), o.optString("b64"),
            )
            "bye" -> FromDesk.Bye(o.optString("reason"))
            else -> FromDesk.Unknown
        }
    }

    // ---- what a phone says ----------------------------------------------

    fun attach(session: String, device: String, since: Long): ByteArray = JSONObject()
        .put("k", "attach").put("session", session).put("device", device).put("since", since)
        .toString().toByteArray()

    fun list(): ByteArray = JSONObject().put("k", "list").toString().toByteArray()

    fun submit(session: String, text: String): ByteArray = JSONObject()
        .put("k", "submit").put("session", session).put("text", text)
        .toString().toByteArray()

    fun interrupt(session: String): ByteArray = JSONObject()
        .put("k", "interrupt").put("session", session).toString().toByteArray()

    fun answerTool(session: String, id: Long, allow: Boolean): ByteArray = JSONObject()
        .put("k", "answer_permission").put("session", session).put("id", id).put("allow", allow)
        .toString().toByteArray()

    fun detach(session: String): ByteArray = JSONObject()
        .put("k", "detach").put("session", session).toString().toByteArray()

    /**
     * One answer to a one-question ask: the option picked, the note typed, or both. The
     * harness reads a `reply` tag and one answer per question.
     */
    fun answerAsk(session: String, id: Long, picked: String?, note: String): ByteArray {
        val answer = JSONObject()
        if (!picked.isNullOrEmpty()) answer.put("picked", JSONArray().put(picked))
        if (note.isNotEmpty()) answer.put("note", note)
        val reply = JSONObject().put("reply", "answered").put("answers", JSONArray().put(answer))
        return JSONObject().put("k", "answer_ask").put("session", session).put("id", id).put("reply", reply)
            .toString().toByteArray()
    }

    fun dismissAsk(session: String, id: Long): ByteArray = JSONObject()
        .put("k", "answer_ask").put("session", session).put("id", id)
        .put("reply", JSONObject().put("reply", "dismissed"))
        .toString().toByteArray()

    fun rename(session: String, name: String): ByteArray = JSONObject()
        .put("k", "rename").put("session", session).put("name", name).toString().toByteArray()

    fun resume(session: String): ByteArray = JSONObject()
        .put("k", "resume").put("session", session).toString().toByteArray()

    fun newSession(cwd: String, prompt: String?): ByteArray = JSONObject()
        .put("k", "new_session").put("cwd", cwd)
        .also { if (!prompt.isNullOrBlank()) it.put("prompt", prompt) }
        .toString().toByteArray()

    // ---- the small readers ----------------------------------------------

    private fun readSessions(array: JSONArray?): List<Session> {
        val out = ArrayList<Session>(array?.length() ?: 0)
        for (o in objects(array)) {
            out.add(
                Session(
                    id = o.optString("id"),
                    name = if (o.isNull("name")) null else o.optString("name"),
                    title = o.optString("title"),
                    cwd = o.optString("cwd"),
                    model = o.optString("model"),
                    startedMs = o.optLong("started_ms"),
                    messages = o.optInt("messages"),
                    live = o.optBoolean("live"),
                ),
            )
        }
        return out
    }

    private fun objects(array: JSONArray?): List<JSONObject> {
        if (array == null) return emptyList()
        val out = ArrayList<JSONObject>(array.length())
        for (i in 0 until array.length()) array.optJSONObject(i)?.let { out.add(it) }
        return out
    }

    private fun strings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val out = ArrayList<String>(array.length())
        for (i in 0 until array.length()) array.optString(i).takeIf { it.isNotBlank() }?.let { out.add(it) }
        return out
    }

    private fun labels(options: JSONArray?): List<String> {
        if (options == null) return emptyList()
        val out = ArrayList<String>(options.length())
        for (i in 0 until options.length()) {
            val label = options.optJSONObject(i)?.optString("label") ?: options.optString(i)
            if (label.isNotBlank()) out.add(label)
        }
        return out
    }

    /**
     * One event from the `--json` stream, as something to show. The shapes are
     * the harness's, and unknown ones are skipped rather than guessed at.
     */
    fun eventText(event: JSONObject): String? = when (event.optString("type")) {
        "text" -> event.optString("text")
        "error" -> "\n" + event.optString("error")
        "tool_denied" -> "\n_" + event.optJSONObject("call")?.optJSONObject("function")?.optString("name").orEmpty().ifBlank { "a tool" } + " was not allowed_\n"
        "notice" -> "\n_" + event.optString("text") + "_\n"
        else -> null
    }

    fun eventTool(event: JSONObject): String? = when (event.optString("type")) {
        "tool_start" -> event.optJSONObject("call")?.optJSONObject("function")?.optString("name")
        else -> null
    }
}
