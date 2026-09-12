package io.github.kasecrab.razorback.remote

import android.os.Handler
import android.os.Looper
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
    private val plink = Crypto.newLink()

    @Volatile private var armed = false
    private var ws: WebSocketClient? = null
    private var backoffMs = 400L

    /** The relay's own numbering, which is what survives a reconnect. */
    @Volatile var cursor: Long = 0
        private set

    // Set on the reader thread, read on the main thread when something is sent.
    @Volatile private var deskLink: ByteArray? = null
    @Volatile private var sealer: Crypto.Sealer? = null
    @Volatile private var opener: Crypto.Opener? = null
    /** How far each machine link has been read this session, so a link that comes round again does not start from nought. */
    private val windows = HashMap<String, Long>()

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
        val seal = sealer ?: return
        val desk = deskLink ?: return
        val (seq, ct) = seal.seal(payload)
        ws?.sendText(Frames.command(Crypto.hex(desk), Crypto.hex(plink), seq, ct))
    }

    /** Whether anything said now would actually reach the machine. */
    fun ready(): Boolean = sealer != null && ws?.isOpen == true

    private fun dial() {
        if (!armed || ws != null) return
        val ts = System.currentTimeMillis()
        val nonce = Crypto.newNonce()
        val signature = keys.signConnect("phone", ts, nonce)
        val dialled = WebSocketClient(
            "${RelayUrl.socket(url)}/hub/${keys.hub}?r=phone&ts=$ts&n=${esc(nonce)}&h=${esc(signature)}",
            emptyMap(),
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
                rekey(frame.link)
                val open = opener ?: return
                val opened = open.open(frame.seq, frame.ct)
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
                    else -> frame.error
                }
                val fatal = frame.error == "revoked"
                main.post { listener.onTrouble(text, fatal) }
            }
            else -> {}
        }
    }

    /**
     * The machine's key changes when it reconnects, and every frame says which
     * link it belongs to, so the change is noticed rather than announced.
     */
    private fun rekey(link: String) {
        val desk = Crypto.unhex(link) ?: return
        if (deskLink?.contentEquals(desk) == true) return
        deskLink?.let { windows[Crypto.hex(it)] = opener?.seq ?: 0L }
        if (windows.size > WINDOWS_KEPT) windows.clear()
        deskLink = desk
        opener = Crypto.Opener(
            keys.linkKey(Crypto.Dir.D2P, desk, plink), Crypto.Dir.D2P, desk, ByteArray(16),
        ).also { fresh -> windows[link]?.let { fresh.resumeFrom(it) } }
        sealer = Crypto.Sealer(
            keys.linkKey(Crypto.Dir.P2D, desk, plink), Crypto.Dir.P2D, desk, plink,
        )
    }

    /** The one place that decides whether to dial again. */
    private fun dropped(socket: WebSocketClient, error: Throwable?) {
        if (ws !== socket) return
        ws = null
        sealer = null
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
        const val WINDOWS_KEPT = 64
    }

    private fun esc(s: String): String = buildString {
        for (b in s.toByteArray()) {
            val c = b.toInt().toChar()
            if (c.isLetterOrDigit() || c in "-._~") append(c) else append("%%%02X".format(b))
        }
    }
}
