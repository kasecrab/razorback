package io.github.kasecrab.razorback.voice

import android.os.Handler
import android.os.Looper
import io.github.kasecrab.razorback.core.Log
import io.github.kasecrab.razorback.core.arr
import io.github.kasecrab.razorback.core.bool
import io.github.kasecrab.razorback.core.obj
import io.github.kasecrab.razorback.core.str
import io.github.kasecrab.razorback.core.ws.HandshakeException
import io.github.kasecrab.razorback.core.ws.WebSocketClient
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Deepgram live transcription for typing by voice. The socket is dialled before the
 * first word and kept warm with KeepAlive, because the handshake costs more than a
 * second and an idle socket is free. Audio only flows while [listening].
 */
class DictationLink(
    private val key: () -> String?,
    private val model: String = "nova-3",
    private val language: String = "",
) {
    interface Listener {
        fun onInterim(text: String)
        fun onFinal(text: String)
        fun onUtteranceEnd()
        fun onState(state: State)
        fun onError(message: String)
    }

    enum class State { IDLE, CONNECTING, READY, LISTENING, FAILED }

    var listener: Listener? = null
    @Volatile var state: State = State.IDLE
        private set(value) {
            field = value
            main.post { listener?.onState(value) }
        }

    private val main = Handler(Looper.getMainLooper())
    private var ws: WebSocketClient? = null
    @Volatile private var listening = false
    @Volatile private var armed = false
    private var backoffMs = BACKOFF_MIN
    private var lastSend = 0L
    private val keepAlive = object : Runnable {
        override fun run() {
            val socket = ws
            if (socket != null && socket.isOpen && System.currentTimeMillis() - lastSend >= KEEPALIVE_MS) {
                try {
                    socket.sendText("{\"type\":\"KeepAlive\"}")
                    lastSend = System.currentTimeMillis()
                } catch (e: IOException) {
                    Log.d { "keepalive failed: ${e.message}" }
                }
            }
            if (armed) main.postDelayed(this, KEEPALIVE_MS)
        }
    }

    /** Open the socket now so speech can start without waiting. */
    fun warm() {
        if (armed) return
        armed = true
        dial()
        main.postDelayed(keepAlive, KEEPALIVE_MS)
    }

    fun listen(on: Boolean) {
        if (listening == on) return
        listening = on
        if (on) {
            if (!armed) warm()
            state = if (ws?.isOpen == true) State.LISTENING else State.CONNECTING
        } else {
            // The tail of the last word is already queued; ask Deepgram to flush it.
            try {
                ws?.takeIf { it.isOpen }?.sendText("{\"type\":\"Finalize\"}")
            } catch (e: IOException) {
                Log.d { "finalize failed: ${e.message}" }
            }
            state = if (ws?.isOpen == true) State.READY else state
        }
    }

    /** From the audio thread. Dropped unless listening and the socket is up. */
    fun audio(chunk: ByteArray, len: Int) {
        if (!listening) return
        val socket = ws ?: return
        if (!socket.isOpen) return
        try {
            socket.sendBinary(chunk, 0, len)
            lastSend = System.currentTimeMillis()
        } catch (e: IOException) {
            Log.d { "audio send failed: ${e.message}" }
        }
    }

    fun stop() {
        armed = false
        listening = false
        main.removeCallbacks(keepAlive)
        val socket = ws
        ws = null
        if (socket != null) {
            Thread {
                try {
                    if (socket.isOpen) socket.sendText("{\"type\":\"CloseStream\"}")
                } catch (_: IOException) {
                }
                socket.close()
            }.start()
        }
        state = State.IDLE
    }

    private fun url(): String {
        val sb = StringBuilder("wss://api.deepgram.com/v1/listen?model=")
        sb.append(enc(model))
        sb.append("&encoding=linear16&sample_rate=16000&channels=1&interim_results=true&punctuate=true&smart_format=true&endpointing=400&utterance_end_ms=1000&filler_words=false")
        if (language.isNotBlank()) sb.append("&language=").append(enc(language))
        return sb.toString()
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun dial() {
        val apiKey = key()
        if (apiKey == null) {
            state = State.FAILED
            main.post { listener?.onError("no Deepgram key") }
            armed = false
            return
        }
        state = State.CONNECTING
        val socket = WebSocketClient(url(), mapOf("Authorization" to "Token $apiKey"), object : WebSocketClient.Listener {
            override fun onOpen(ws: WebSocketClient) {
                backoffMs = BACKOFF_MIN
                lastSend = System.currentTimeMillis()
                state = if (listening) State.LISTENING else State.READY
            }

            override fun onText(ws: WebSocketClient, text: String) = handle(text)

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
            state = State.FAILED
            main.post { listener?.onError("Deepgram refused the key") }
            return
        }
        Log.w("dictation socket dropped: ${error?.message ?: "closed"}")
        state = State.CONNECTING
        val wait = backoffMs
        backoffMs = minOf(backoffMs * 2, BACKOFF_MAX)
        main.postDelayed({ if (armed && ws == null) dial() }, wait)
    }

    private fun handle(text: String) {
        val json = try {
            JSONObject(text)
        } catch (_: JSONException) {
            return
        }
        when (json.str("type")) {
            "Results" -> {
                val transcript = json.obj("channel")?.arr("alternatives")?.optJSONObject(0)?.str("transcript")?.trim().orEmpty()
                if (transcript.isEmpty()) return
                val isFinal = json.bool("is_final") ?: false
                main.post { if (isFinal) listener?.onFinal(transcript) else listener?.onInterim(transcript) }
            }
            "UtteranceEnd" -> main.post { listener?.onUtteranceEnd() }
        }
    }

    private companion object {
        const val KEEPALIVE_MS = 5000L
        const val BACKOFF_MIN = 400L
        const val BACKOFF_MAX = 16000L
    }
}
