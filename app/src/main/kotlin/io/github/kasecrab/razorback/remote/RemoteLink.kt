package io.github.kasecrab.razorback.remote

import io.github.kasecrab.razorback.core.Keys
import io.github.kasecrab.razorback.core.Prefs
import io.github.kasecrab.razorback.core.Secrets
import io.github.kasecrab.razorback.data.RemoteStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The paired machine, as the rest of the app sees it.
 *
 * Holds the socket, writes down what arrives, and tells whoever is looking.
 * Everything below it deals in sealed frames; everything above it deals in
 * sessions and text.
 */
class RemoteLink(
    private val prefs: Prefs,
    private val secrets: Secrets,
    private val store: RemoteStore,
    private val scope: CoroutineScope,
) : RelayClient.Listener {

    interface Watcher {
        fun onSessions(sessions: List<Frames.Session>) {}
        fun onEvents(session: String, events: List<JSONObject>) {}
        /** Whole messages, from before this phone was listening. */
        fun onSnapshot(session: String, messages: List<JSONObject>) {}
        fun onState(state: Frames.SessionState) {}
        fun onAsk(question: Frames.Question, isTool: Boolean) {}
        fun onAnswered(id: Long, by: String) {}
        fun onTrouble(text: String) {}
        fun onMachine(machine: Frames.Machine) {}
    }

    private val watchers = ArrayList<Watcher>()
    private var client: RelayClient? = null
    private var hub: String = ""

    /** What the machine last said about itself, for anything that asks late. */
    var machine: Frames.Machine? = null
        private set
    var sessions: List<Frames.Session> = emptyList()
        private set
    var attached: String = ""
        private set

    val paired: Boolean get() = secrets.has(Secrets.RELAY) && url().isNotEmpty()

    fun add(watcher: Watcher) {
        watchers.add(watcher)
    }

    fun remove(watcher: Watcher) {
        watchers.remove(watcher)
    }

    /** Remember a pairing and connect with it. */
    fun pair(url: String, code: String): Boolean {
        val raw = Codes.parse(code) ?: return false
        prefs[Keys.RELAY_URL] = url.trim().trimEnd('/')
        secrets.put(Secrets.RELAY, code)
        hub = Crypto.Keys(raw).hub
        scope.launch { store.rememberMachine(hub, "a machine", url) }
        stop()
        start()
        return true
    }

    fun forget() {
        val gone = hub
        stop()
        secrets.put(Secrets.RELAY, null)
        prefs[Keys.RELAY_URL] = ""
        if (gone.isNotEmpty()) scope.launch { store.forget(gone) }
    }

    fun start() {
        if (client != null) return
        val code = secrets.get(Secrets.RELAY) ?: return
        val raw = Codes.parse(code) ?: return
        val url = url()
        if (url.isEmpty()) return
        hub = Crypto.Keys(raw).hub
        val started = RelayClient(url, raw, this)
        client = started
        scope.launch {
            // Picking up where this phone left off rather than asking for
            // everything the relay still has.
            val from = store.machines().firstOrNull { it.hub == hub }?.cursor ?: 0
            started.start(from)
        }
    }

    fun stop() {
        client?.stop()
        client = null
    }

    fun ready(): Boolean = client?.ready() == true

    fun attach(session: String) {
        attached = session
        client?.send(Frames.attach(session, "phone", 0))
    }

    fun submit(text: String) {
        client?.send(Frames.submit(attached, text))
    }

    fun interrupt() {
        client?.send(Frames.interrupt(attached))
    }

    fun answerTool(id: Long, allow: Boolean) {
        client?.send(Frames.answerTool(attached, id, allow))
    }

    fun resume(session: String) {
        client?.send(Frames.resume(session))
    }

    fun refresh() {
        client?.send(Frames.list())
    }

    // ---- what the socket says -------------------------------------------

    override fun onLink() {
        refresh()
        if (attached.isNotEmpty()) attach(attached)
    }

    override fun onPayload(payload: Frames.FromDesk) {
        when (payload) {
            is Frames.FromDesk.Hello -> {
                machine = payload.machine
                scope.launch { store.rememberMachine(hub, payload.machine.host, url()) }
                watchers.forEach { it.onMachine(payload.machine) }
            }
            is Frames.FromDesk.Sessions -> {
                sessions = payload.list
                scope.launch { store.rememberSessions(hub, payload.list) }
                watchers.forEach { it.onSessions(payload.list) }
            }
            is Frames.FromDesk.State -> watchers.forEach { it.onState(payload.state) }
            is Frames.FromDesk.Events -> {
                val cursor = client?.cursor ?: 0
                scope.launch {
                    store.rememberEvents(payload.session, cursor, payload.events)
                    store.rememberCursor(hub, cursor)
                }
                watchers.forEach { it.onEvents(payload.session, payload.events) }
            }
            is Frames.FromDesk.Snapshot ->
                watchers.forEach { it.onSnapshot(payload.session, payload.messages) }
            is Frames.FromDesk.Ask ->
                watchers.forEach { it.onAsk(payload.question, payload.isTool) }
            is Frames.FromDesk.Answered ->
                watchers.forEach { it.onAnswered(payload.id, payload.by) }
            is Frames.FromDesk.Ack ->
                payload.error?.let { why -> watchers.forEach { it.onTrouble(why) } }
            is Frames.FromDesk.Notice -> watchers.forEach { it.onTrouble(payload.text) }
            is Frames.FromDesk.Bye ->
                watchers.forEach { it.onTrouble("that machine has gone") }
            else -> {}
        }
    }

    override fun onGap(from: Long) {
        watchers.forEach { it.onTrouble("earlier output is no longer kept") }
    }

    override fun onTrouble(text: String, fatal: Boolean) {
        if (fatal) stop()
        watchers.forEach { it.onTrouble(text) }
    }

    private fun url(): String = prefs[Keys.RELAY_URL]
}
