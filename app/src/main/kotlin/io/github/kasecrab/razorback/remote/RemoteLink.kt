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
        /** The socket came up or went down; [ready] says which. */
        fun onLink() {}
        /** A session this phone asked for has started and is ready to be opened. */
        fun onSessionStarted(session: String) {}
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

    /** The question the machine is waiting on, until somebody answers it. */
    var pending: Frames.Question? = null
        private set
    var pendingIsTool: Boolean = false
        private set

    /** Set by [newSession]: the next session list is looked through for the one that was asked for. */
    private var openNewest = false

    val paired: Boolean get() = secrets.has(Secrets.RELAY) && url().isNotEmpty()

    /**
     * Whether the machine itself is there, as distinct from the relay. The relay keeps and
     * replays what the machine said, so a socket that is open and reading tells nothing
     * about the machine; only an answer to something this phone asked does. Every command
     * is such a question: the relay says "offline" at once when no machine is connected,
     * and the machine's own frames say it is.
     */
    var machineUp: Boolean = false
        private set(value) {
            if (field == value) return
            field = value
            watchers.forEach { it.onLink() }
        }

    /** The relay socket is open and the machine has answered on it. */
    val connected: Boolean get() = ready() && machineUp

    /** Ask the machine for its list, which doubles as a check that it is there. */
    fun probe() {
        if (client?.ready() == true) refresh()
    }

    fun add(watcher: Watcher) {
        watchers.add(watcher)
    }

    fun remove(watcher: Watcher) {
        watchers.remove(watcher)
    }

    /** Remember a pairing and connect with it. */
    fun pair(url: String, code: String): Boolean {
        val raw = Codes.parse(code) ?: return false
        if (!RelayUrl.acceptable(url)) return false
        prefs[Keys.RELAY_URL] = RelayUrl.clean(url)
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
            // What the machine said last time, so the list is there before the socket is.
            if (sessions.isEmpty()) {
                val kept = store.sessions(hub)
                if (kept.isNotEmpty() && sessions.isEmpty()) {
                    sessions = kept.map { Frames.Session(it.id, null, it.title, it.cwd, it.model, it.startedMs, 0, it.live) }
                    watchers.forEach { it.onSessions(sessions) }
                }
            }
            // Picking up where this phone left off rather than asking for
            // everything the relay still has.
            val from = store.machines().firstOrNull { it.hub == hub }?.cursor ?: 0
            // Stopped, or paired afresh, while the cursor was being read: this one never dials.
            if (client === started) started.start(from)
        }
    }

    fun stop() {
        client?.stop()
        client = null
    }

    fun ready(): Boolean = client?.ready() == true

    fun attach(session: String) {
        attached = session
        client?.send(Frames.attach(session, "phone", client?.cursor ?: 0))
    }

    /** No longer watching: the machine stops saying who is, and the background link can rest. */
    fun detach() {
        val was = attached
        attached = ""
        if (was.isNotEmpty()) client?.send(Frames.detach(was))
    }

    fun submit(text: String) {
        client?.send(Frames.submit(attached, text))
    }

    fun interrupt() {
        client?.send(Frames.interrupt(attached))
    }

    fun answerTool(id: Long, allow: Boolean) {
        if (pending?.id == id) pending = null
        client?.send(Frames.answerTool(attached, id, allow))
    }

    fun answerAsk(id: Long, picked: String?, note: String) {
        if (pending?.id == id) pending = null
        client?.send(Frames.answerAsk(attached, id, picked, note))
    }

    fun dismissAsk(id: Long) {
        if (pending?.id == id) pending = null
        client?.send(Frames.dismissAsk(attached, id))
    }

    fun resume(session: String) {
        client?.send(Frames.resume(session))
    }

    /** Where the machine will start a session: its offered roots, then where its sessions already run. */
    fun startPlaces(): List<String> {
        val out = LinkedHashSet<String>()
        machine?.roots?.forEach { out.add(it) }
        sessions.sortedByDescending { it.startedMs }.forEach { if (it.cwd.isNotBlank()) out.add(it.cwd) }
        return out.toList()
    }

    /** Whether the machine will start sessions at all: a window publishes the one it has and no others. */
    val canStart: Boolean get() = machine?.let { it.roots.isNotEmpty() || it.holder == "daemon" } ?: true

    /** Start a session in [cwd] on the machine; the one that appears is announced through [Watcher.onSessionStarted]. */
    fun newSession(cwd: String) {
        openNewest = true
        startingIn = cwd
        client?.send(Frames.newSession(cwd, null))
    }

    /** The directory the last new session was asked for, for the screen that opens before the list knows it. */
    var startingIn: String = ""
        private set

    fun rename(session: String, name: String) {
        client?.send(Frames.rename(session, name))
    }

    fun refresh() {
        client?.send(Frames.list())
    }

    // ---- what the socket says -------------------------------------------

    override fun onLink() {
        refresh()
        if (attached.isNotEmpty()) attach(attached)
        watchers.forEach { it.onLink() }
    }

    override fun onDown() {
        machineUp = false
        watchers.forEach { it.onLink() }
    }

    override fun onOffline() {
        openNewest = false
        machineUp = false
        watchers.forEach { it.onTrouble("that machine is not connected") }
    }

    override fun onPayload(payload: Frames.FromDesk, n: Long) {
        // Anything the machine says makes it present, until the relay says otherwise. A
        // replay of old frames sets this too, but the "offline" answer to the list asked
        // for on connect follows the replay and puts it right.
        if (payload !is Frames.FromDesk.Bye) machineUp = true
        when (payload) {
            is Frames.FromDesk.Hello -> {
                machine = payload.machine
                scope.launch { store.rememberMachine(hub, payload.machine.host, url()) }
                watchers.forEach { it.onMachine(payload.machine) }
            }
            is Frames.FromDesk.Sessions -> {
                val before = sessions.map { it.id }.toSet()
                sessions = payload.list
                scope.launch { store.rememberSessions(hub, payload.list) }
                watchers.forEach { it.onSessions(payload.list) }
                if (openNewest) {
                    val fresh = payload.list.filter { it.live && it.id !in before }.maxByOrNull { it.startedMs }
                    if (fresh != null) {
                        openNewest = false
                        watchers.forEach { it.onSessionStarted(fresh.id) }
                    }
                }
            }
            is Frames.FromDesk.State -> watchers.forEach { it.onState(payload.state) }
            is Frames.FromDesk.Events -> {
                // Keyed by the frame's own number, not by wherever the socket has got to
                // since: two frames read before this thread turned round would otherwise
                // land under one key and the second would be dropped.
                scope.launch {
                    store.rememberEvents(payload.session, n, payload.events)
                    store.rememberCursor(hub, n)
                }
                watchers.forEach { it.onEvents(payload.session, payload.events) }
            }
            is Frames.FromDesk.Snapshot ->
                watchers.forEach { it.onSnapshot(payload.session, payload.messages) }
            is Frames.FromDesk.Ask -> {
                pending = payload.question
                pendingIsTool = payload.isTool
                watchers.forEach { it.onAsk(payload.question, payload.isTool) }
            }
            is Frames.FromDesk.Answered -> {
                if (pending?.id == payload.id) pending = null
                watchers.forEach { it.onAnswered(payload.id, payload.by) }
            }
            is Frames.FromDesk.Ack -> {
                val started = payload.session
                if (started != null) {
                    // The machine names the session it started; the list that follows may
                    // not have it yet, since a new session is not on disk until its first turn.
                    openNewest = false
                    watchers.forEach { it.onSessionStarted(started) }
                }
                payload.error?.let { why ->
                    openNewest = false
                    watchers.forEach { it.onTrouble(why) }
                }
            }
            is Frames.FromDesk.Notice -> watchers.forEach { it.onTrouble(payload.text) }
            is Frames.FromDesk.Bye -> {
                val text = when (payload.reason) {
                    "tui_taking_over" -> "a window on the machine took over; carrying on with it"
                    "revoked" -> "this pairing was ended"
                    else -> "that machine has gone"
                }
                if (payload.reason != "tui_taking_over") machineUp = false
                watchers.forEach { it.onTrouble(text) }
            }
            else -> {}
        }
    }

    /** What was missed is gone from the relay; a fresh attach brings whole messages instead. */
    override fun onGap(from: Long) {
        watchers.forEach { it.onTrouble("earlier output is no longer kept") }
        if (attached.isNotEmpty()) attach(attached)
    }

    override fun onTrouble(text: String, fatal: Boolean) {
        if (fatal) stop()
        watchers.forEach { it.onTrouble(text) }
    }

    private fun url(): String = prefs[Keys.RELAY_URL]
}
