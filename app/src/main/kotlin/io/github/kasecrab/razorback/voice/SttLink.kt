package io.github.kasecrab.razorback.voice

import android.os.Handler
import android.os.Looper
import io.github.kasecrab.razorback.core.Log
import io.github.kasecrab.razorback.core.str
import io.github.kasecrab.razorback.core.ws.HandshakeException
import io.github.kasecrab.razorback.core.ws.WebSocketClient
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * Deepgram Flux: transcription with turn detection built in. Audio streams the whole
 * time; the model says when the person starts, pauses and finishes a turn.
 */
class SttLink(
    private val key: () -> String?,
    private val model: () -> String,
    /** One of "quick", "balanced", "patient": how long a pause counts as the end of a turn. */
    private val turn: () -> String = { "balanced" },
) : Ears {

    override var listener: Ears.Listener? = null
    override val isOpen: Boolean get() = ws?.isOpen == true

    private val main = Handler(Looper.getMainLooper())
    private var ws: WebSocketClient? = null
    @Volatile private var armed = false
    private var backoffMs = 400L

    override fun start() {
        if (armed) return
        armed = true
        dial()
    }

    override fun audio(chunk: ByteArray, len: Int) {
        val socket = ws ?: return
        if (!socket.isOpen) return
        try {
            socket.sendBinary(chunk, 0, len)
        } catch (e: IOException) {
            Log.d { "stt send failed: ${e.message}" }
        }
    }

    override fun keepAlive() {
        val socket = ws ?: return
        if (socket.isOpen) socket.sendText("{\"type\":\"KeepAlive\"}")
    }

    override fun stop() {
        armed = false
        val socket = ws
        ws = null
        if (socket != null) {
            if (socket.isOpen) socket.sendText("{\"type\":\"CloseStream\"}")
            socket.close()
        }
    }

    private fun url(): String {
        val m = model()
        val sb = StringBuilder("wss://api.deepgram.com/v2/listen?model=")
        sb.append(java.net.URLEncoder.encode(m, "UTF-8"))
        sb.append("&encoding=linear16&sample_rate=").append(MicCapture.SAMPLE_RATE)
        // A lower threshold answers sooner but may cut a slow speaker off; the timeout is the
        // silence after which the turn ends whatever the confidence.
        when (turn()) {
            "quick" -> sb.append("&eot_threshold=0.5&eot_timeout_ms=3000")
            "patient" -> sb.append("&eot_threshold=0.85&eot_timeout_ms=8000")
            else -> sb.append("&eot_threshold=0.7&eot_timeout_ms=5000")
        }
        if (m.endsWith("-multi")) sb.append("&language=multi")
        return sb.toString()
    }

    private fun dial() {
        val apiKey = key()
        if (apiKey == null) {
            armed = false
            main.post { listener?.onError("no Deepgram key") }
            return
        }
        val socket = WebSocketClient(url(), mapOf("Authorization" to "Token $apiKey"), object : WebSocketClient.Listener {
            override fun onOpen(ws: WebSocketClient) {
                backoffMs = 400L
            }

            override fun onText(ws: WebSocketClient, text: String) {
                if (this@SttLink.ws === ws) handle(text)
            }

            override fun onClosed(ws: WebSocketClient, code: Int, reason: String) = dropped(ws, null)

            override fun onFailure(ws: WebSocketClient, error: Throwable) = dropped(ws, error)
        })
        ws = socket
        socket.connect()
    }

    private fun dropped(socket: WebSocketClient, error: Throwable?) {
        if (ws !== socket) return
        ws = null
        if (!armed) return
        if (error is HandshakeException && (error.status == 401 || error.status == 403)) {
            armed = false
            main.post { listener?.onError(DeepgramAccount.KEY_REFUSED) }
            return
        }
        Log.w("flux socket dropped: ${error?.message ?: "closed"}")
        main.post { listener?.onDropped(true) }
        val wait = backoffMs
        backoffMs = minOf(backoffMs * 2, 16000L)
        main.postDelayed({ if (armed && ws == null) dial() }, wait)
    }

    private fun handle(text: String) {
        val json = try {
            JSONObject(text)
        } catch (_: JSONException) {
            return
        }
        when (json.str("type")) {
            "Connected" -> main.post { listener?.onConnected() }
            "TurnInfo" -> {
                val kind = when (json.str("event")) {
                    "StartOfTurn" -> Ears.Turn.START
                    "Update" -> Ears.Turn.UPDATE
                    "EagerEndOfTurn" -> Ears.Turn.EAGER_END
                    "TurnResumed" -> Ears.Turn.RESUMED
                    "EndOfTurn" -> Ears.Turn.END
                    else -> return
                }
                val transcript = json.str("transcript")?.trim().orEmpty()
                val index = json.optInt("turn_index", 0)
                main.post { listener?.onTurn(kind, transcript, index, 1f) }
            }
            // The frame is the vendor's own text, so the log that is on in release says
            // only that one arrived; the words are for a build being worked on.
            "Error" -> {
                Log.w("flux reported an error")
                Log.d { "flux: $text" }
            }
        }
    }
}
