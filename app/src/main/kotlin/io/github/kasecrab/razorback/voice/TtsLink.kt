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
import java.net.URLEncoder

/**
 * Deepgram Aura streaming text to speech. Sentences go in as they finish; audio comes
 * back as raw PCM. Text sent before the socket is up waits in a queue.
 */
class TtsLink(
    private val key: () -> String?,
    private val model: () -> String,
    private val speed: () -> Float,
) {
    interface Listener {
        fun onAudio(data: ByteArray)
        fun onFlushed()
        fun onCleared()
        fun onError(message: String)
    }

    var listener: Listener? = null
    val isOpen: Boolean get() = ws?.isOpen == true

    private val main = Handler(Looper.getMainLooper())
    private var ws: WebSocketClient? = null
    private val queue = ArrayList<String>()
    @Volatile private var armed = false
    private var backoffMs = 400L

    fun warm() {
        if (armed) return
        armed = true
        dial()
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        send(json("Speak", text))
    }

    fun flush() = send("{\"type\":\"Flush\"}")

    /** Drop everything queued upstream; for barge-in. */
    fun clear() {
        synchronized(queue) { queue.clear() }
        val socket = ws
        if (socket != null && socket.isOpen) {
            try {
                socket.sendText("{\"type\":\"Clear\"}")
            } catch (e: IOException) {
                Log.d { "clear failed: ${e.message}" }
            }
        }
    }

    fun stop() {
        armed = false
        synchronized(queue) { queue.clear() }
        val socket = ws
        ws = null
        if (socket != null) {
            if (socket.isOpen) socket.sendText("{\"type\":\"Close\"}")
            socket.close()
        }
    }

    private fun send(frame: String) {
        if (!armed) warm()
        val socket = ws
        if (socket == null || !socket.isOpen) {
            synchronized(queue) { queue.add(frame) }
            if (socket == null) dial()
            return
        }
        try {
            socket.sendText(frame)
        } catch (e: IOException) {
            Log.w("tts send failed: ${e.message}")
            synchronized(queue) { queue.add(frame) }
        }
    }

    private fun json(type: String, text: String): String {
        val o = JSONObject()
        o.put("type", type)
        o.put("text", text)
        return o.toString()
    }

    private fun url(): String {
        val sb = StringBuilder("wss://api.deepgram.com/v1/speak?model=")
        sb.append(URLEncoder.encode(model(), "UTF-8"))
        sb.append("&encoding=linear16&sample_rate=").append(Playback.SAMPLE_RATE)
        val s = speed()
        if (s != 1f) sb.append("&speed=").append(String.format(java.util.Locale.US, "%.2f", s))
        return sb.toString()
    }

    private fun dial() {
        if (ws != null) return
        val apiKey = key()
        if (apiKey == null) {
            main.post { listener?.onError("no Deepgram key") }
            armed = false
            return
        }
        val socket = WebSocketClient(url(), mapOf("Authorization" to "Token $apiKey"), object : WebSocketClient.Listener {
            override fun onOpen(ws: WebSocketClient) {
                backoffMs = 400L
                val pending = synchronized(queue) { ArrayList(queue).also { queue.clear() } }
                for (f in pending) {
                    try {
                        ws.sendText(f)
                    } catch (e: IOException) {
                        Log.w("tts replay failed: ${e.message}")
                    }
                }
            }

            override fun onBinary(ws: WebSocketClient, data: ByteArray) {
                // A socket that stop() let go of may still drain for a moment; its audio is not ours.
                if (this@TtsLink.ws !== ws) return
                listener?.onAudio(data)
            }

            override fun onText(ws: WebSocketClient, text: String) {
                if (this@TtsLink.ws !== ws) return
                val type = try {
                    JSONObject(text).str("type")
                } catch (_: JSONException) {
                    null
                }
                when (type) {
                    "Flushed" -> main.post { listener?.onFlushed() }
                    "Cleared" -> main.post { listener?.onCleared() }
                    "Warning", "Error" -> Log.w("tts: $text")
                }
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
            main.post { listener?.onError("Deepgram refused the key") }
            return
        }
        // Reconnect straight away if something is waiting; otherwise the next speak() dials.
        val waiting = synchronized(queue) { queue.isNotEmpty() }
        if (waiting) {
            val wait = backoffMs
            backoffMs = minOf(backoffMs * 2, 8000L)
            main.postDelayed({ if (armed && ws == null) dial() }, wait)
        }
    }
}
