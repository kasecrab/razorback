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
        /** One payload, already opened. */
        fun onPayload(payload: Frames.FromDesk) {}
        /** Everything before this is gone from the relay. */
        fun onGap(from: Long) {}
        /** Something worth putting in front of a person. */
        fun onTrouble(text: String, fatal: Boolean) {}
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

    private var deskLink: ByteArray? = null
    private var sealer: Crypto.Sealer? = null
    private var opener: Crypto.Opener? = null

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
            "${socketUrl()}/hub/${keys.hub}?r=phone&ts=$ts&n=${esc(nonce)}&h=${esc(signature)}",
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
                main.post { listener.onPayload(payload) }
            }
            is Frames.Envelope.Gap -> {
                cursor = frame.from
                main.post { listener.onGap(frame.from) }
            }
            is Frames.Envelope.Ctl -> {
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
        deskLink = desk
        opener = Crypto.Opener(
            keys.linkKey(Crypto.Dir.D2P, desk, plink), Crypto.Dir.D2P, desk, ByteArray(16),
        )
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
        if (!armed) return
        // A refusal will be refused again in exactly the same way.
        if (error is HandshakeException && (error.status == 401 || error.status == 403)) {
            armed = false
            main.post { listener.onTrouble("this phone is not paired with that machine", true) }
            return
        }
        if (error is HandshakeException && error.status == 410) {
            armed = false
            main.post { listener.onTrouble("this pairing was ended", true) }
            return
        }
        val wait = backoffMs
        backoffMs = minOf(backoffMs * 2, 16_000L)
        main.postDelayed({ if (armed && ws == null) dial() }, wait)
    }

    private fun socketUrl(): String {
        val trimmed = url.trim().trimEnd('/')
        return when {
            trimmed.startsWith("https://") -> "wss://" + trimmed.substring(8)
            trimmed.startsWith("http://") -> "ws://" + trimmed.substring(7)
            else -> trimmed
        }
    }

    private fun esc(s: String): String = buildString {
        for (b in s.toByteArray()) {
            val c = b.toInt().toChar()
            if (c.isLetterOrDigit() || c in "-._~") append(c) else append("%%%02X".format(b))
        }
    }
}
