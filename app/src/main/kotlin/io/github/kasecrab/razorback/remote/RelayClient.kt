package io.github.kasecrab.razorback.remote

import android.os.Handler
import android.os.Looper
import io.github.kasecrab.razorback.core.Log
import io.github.kasecrab.razorback.core.ws.HandshakeException
import io.github.kasecrab.razorback.core.ws.WebSocketClient

/**
 * The socket to a paired machine's relay.
 *
 * `WebSocketClient` deliberately has no reconnect of its own, so that lives
 * here, in the shape the voice links already use: one current socket, an
 * identity check on every callback, and a single funnel that decides whether
 * to dial again.
 *
 * Nothing above this sees a frame that has not been opened, and nothing below
 * it sees one that has.
 */
class RelayClient(
    private val url: String,
    private val code: ByteArray,
    private val listener: Listener,
) {

    interface Listener {
        /** The link is up. Anything the machine needs told goes now. */
        fun onLink() {}
        /** The link is down; it dials again on its own unless [onTrouble] said it was fatal. */
        fun onDown() {}
        /** One payload, already opened, with the relay's number for the frame it came in. */
        fun onPayload(payload: Frames.FromDesk, n: Long) {}
        /** Everything before this is gone from the relay. */
        fun onGap(from: Long) {}
        /** Something worth putting in front of a person. */
        fun onTrouble(text: String, fatal: Boolean) {}
        /** The relay had nowhere to send what was just said: no machine is connected to it. */
        fun onOffline() {}
    }

    private val main = Handler(Looper.getMainLooper())
    private val keys = Crypto.Keys(code)

    @Volatile private var armed = false
    private var ws: WebSocketClient? = null
    private var backoffMs = 400L

    /** The relay's own numbering, which is what survives a reconnect. */
    @Volatile var cursor: Long = 0
        private set

    // Set on the reader thread, read on the main thread when something is sent.
    @Volatile private var deskLink: ByteArray? = null
    @Volatile private var outgoing: Crypto.Outgoing? = null
    @Volatile private var opener: Crypto.Opener? = null
    /** How far each machine link has been read, so a link that comes round again does not start from nought. */
    private val windows = ReplayWindows()

    fun start(from: Long = 0) {
        cursor = from
        armed = true
        dial()
    }

    fun stop() {
        armed = false
        ws?.close()
        ws = null
    }

    /** Send something to the machine. Silently dropped while the link is down. */
    fun send(payload: ByteArray) {
        val out = outgoing ?: return
        val (seq, ct) = out.seal(payload)
        ws?.sendText(Frames.command(Crypto.hex(out.desk), Crypto.hex(out.plink), seq, ct))
    }

    /** Whether anything said now would actually reach the machine. */
    fun ready(): Boolean = outgoing != null && ws?.isOpen == true

    private fun dial() {
        if (!armed || ws != null) return
        val ts = System.currentTimeMillis()
        val nonce = Crypto.newNonce()
        val signature = keys.signConnect("phone", ts, nonce)
        // The signature rides in a header, not the query: a URL is what request logs,
        // proxies and access records keep. The nonce is fresh on every dial, since the
        // relay honours each one exactly once.
        val dialled = WebSocketClient(
            "${RelayUrl.socket(url)}/hub/${keys.hub}?r=phone&ts=$ts&n=${esc(nonce)}",
            mapOf(AUTH_HEADER to signature),
            object : WebSocketClient.Listener {
                override fun onOpen(ws: WebSocketClient) {
                    if (this@RelayClient.ws !== ws) return
                    backoffMs = 400L
                    ws.sendText(Frames.subscribe(cursor, 200))
                    main.post { listener.onLink() }
                }

                override fun onText(ws: WebSocketClient, text: String) {
                    if (this@RelayClient.ws !== ws) return
                    arrived(text)
                }

                override fun onClosed(ws: WebSocketClient, code: Int, reason: String) {
                    dropped(ws, null)
                }

                override fun onFailure(ws: WebSocketClient, error: Throwable) {
                    dropped(ws, error)
                }
            },
        )
        ws = dialled
        dialled.connect()
    }

    /** One frame off the socket, on the reader thread. */
    private fun arrived(text: String) {
        when (val frame = Frames.readEnvelope(text)) {
            is Frames.Envelope.Evt -> {
                cursor = frame.n
                val opened = openEvent(frame) ?: return
                // A replay is expected: the relay hands back what it kept,
                // and some of it may already have been read.
                if (opened.why == Crypto.Refusal.REPLAY) return
                val plain = opened.plain ?: return
                val payload = Frames.readPayload(plain) ?: return
                val n = frame.n
                main.post { listener.onPayload(payload, n) }
            }
            is Frames.Envelope.Gap -> {
                cursor = frame.from
                main.post { listener.onGap(frame.from) }
            }
            is Frames.Envelope.Ctl -> {
                if (frame.error == "offline") {
                    main.post { listener.onOffline() }
                    return
                }
                val text = when (frame.error) {
                    "offline" -> "that machine is not connected"
                    "quota" -> "the relay has done all it will today"
                    "revoked" -> "this pairing was ended"
                    "skew" -> "this phone's clock is too far out"
                    // The relay writes this field and is trusted with nothing else it
                    // sends, so a word it made up does not get put in front of a person
                    // as though the app had said it.
                    else -> {
                        Log.d { "the relay sent a control frame this build does not know: ${frame.error}" }
                        "the relay said something this app does not understand"
                    }
                }
                val fatal = frame.error == "revoked"
                main.post { listener.onTrouble(text, fatal) }
            }
            else -> {}
        }
    }

    /**
     * One event frame, opened under the machine link it names.
     *
     * The machine's key changes when it reconnects, and every frame says which
     * link it belongs to, so the change is noticed rather than announced. What
     * the envelope says is the relay's word and nobody else's, though, and the
     * relay is not trusted with a word of what it carries: a link this phone
     * is not already on takes over only once a frame sealed under it has
     * actually opened. An envelope that could move the link by itself would
     * let anything forwarding frames name a link at will, and a link named
     * twice would put a second stream of commands, counting from one again,
     * under a key the first stream had already spent those numbers on.
     */
    private fun openEvent(frame: Frames.Envelope.Evt): Crypto.Opened? {
        val desk = Crypto.unlink(frame.link) ?: return null
        if (deskLink?.contentEquals(desk) == true) return opener?.open(frame.seq, frame.ct)
        // What the machine sends is sealed for every phone at once, so no phone
        // link goes into its key or its seal; zeros stand in for one.
        val none = ByteArray(Crypto.LINK_BYTES)
        val fresh = Crypto.Opener(
            keys.linkKey(Crypto.Dir.D2P, desk, none), Crypto.Dir.D2P, desk, none,
        ).also { it.resumeFrom(windows.resume(desk)) }
        val opened = fresh.open(frame.seq, frame.ct)
        if (opened.plain == null) return null
        keepWindow()
        deskLink = desk
        opener = fresh
        outgoing = keys.outgoing(desk)
        return opened
    }

    /**
     * Put away how far the link being left has been read.
     *
     * Every place that lets go of a link comes through here, because the relay keeps its
     * log and hands it back to whoever subscribes: a window that started again at nothing
     * would open frames this phone has already read, which is the one thing the window is
     * there to stop.
     */
    private fun keepWindow() {
        val desk = deskLink ?: return
        windows.keep(desk, opener?.seq ?: 0L)
    }

    /** The one place that decides whether to dial again. */
    private fun dropped(socket: WebSocketClient, error: Throwable?) {
        if (ws !== socket) return
        ws = null
        // The socket is gone; what has been read on it is not. The same machine link is
        // usually still there on the other side when this dials again, and the relay will
        // offer its log again, so the window goes with it rather than starting over.
        keepWindow()
        outgoing = null
        opener = null
        deskLink = null
        main.post { listener.onDown() }
        if (!armed) return
        // A refusal will be refused again in exactly the same way.
        if (error is HandshakeException) {
            val why = when {
                error.status == 401 && error.body.contains("skew") -> "this phone's clock is more than five minutes out"
                error.status == 401 || error.status == 403 -> "the relay does not know this pairing code"
                error.status == 404 -> "the relay has never heard of this pairing"
                error.status == 410 -> "this pairing was ended"
                else -> null
            }
            if (why != null) {
                armed = false
                main.post { listener.onTrouble(why, true) }
                return
            }
        }
        val wait = backoffMs
        backoffMs = minOf(backoffMs * 2, 16_000L)
        main.postDelayed({ if (armed && ws == null) dial() }, wait)
    }

    private companion object {
        const val AUTH_HEADER = "x-ah-auth"
    }

    private fun esc(s: String): String = buildString {
        for (b in s.toByteArray()) {
            val c = b.toInt().toChar()
            if (c.isLetterOrDigit() || c in "-._~") append(c) else append("%%%02X".format(b))
        }
    }
}
