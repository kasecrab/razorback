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
 * Nova-3 as the ears of voice mode: the same model and formatting as dictation, so what
 * it hears is as accurate, with the turn worked out from its endpointing. A turn starts
 * with the first words, is updated by every interim, and ends when Deepgram marks the
 * speech final or the utterance-end timer runs out.
 */
class NovaEars(
    private val key: () -> String?,
    private val model: () -> String,
    private val language: () -> String,
    /** "quick", "balanced" or "patient": how much silence ends a turn. */
    private val turn: () -> String,
) : Ears {

    override var listener: Ears.Listener? = null
    override val isOpen: Boolean get() = ws?.isOpen == true

    private val main = Handler(Looper.getMainLooper())
    private var ws: WebSocketClient? = null
    @Volatile private var armed = false
    private var backoffMs = 400L
    private val spoken = Spoken()
    private var turnOpen = false
    private var turnIndex = 0
    private var confidence = 1f

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
            Log.d { "nova send failed: ${e.message}" }
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
        spoken.clear()
        turnOpen = false
    }

    private fun url(): String {
        val sb = StringBuilder("wss://api.deepgram.com/v1/listen?model=")
        sb.append(URLEncoder.encode(model(), "UTF-8"))
        sb.append("&encoding=linear16&sample_rate=").append(MicCapture.SAMPLE_RATE)
        sb.append("&channels=1&interim_results=true&punctuate=true&smart_format=true&filler_words=false")
        // Endpointing is the silence that ends a turn; the utterance timer is the fallback
        // when the last word never gets a speech-final result.
        when (turn()) {
            "quick" -> sb.append("&endpointing=150&utterance_end_ms=800")
            "patient" -> sb.append("&endpointing=600&utterance_end_ms=1800")
            else -> sb.append("&endpointing=280&utterance_end_ms=1000")
        }
        val lang = language()
        if (lang.isNotBlank()) sb.append("&language=").append(URLEncoder.encode(lang, "UTF-8"))
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
                main.post { listener?.onConnected() }
            }

            override fun onText(ws: WebSocketClient, text: String) {
                if (this@NovaEars.ws === ws) handle(text)
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
        Log.w("nova socket dropped: ${error?.message ?: "closed"}")
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
            "Results" -> {
                val alt = json.obj("channel")?.arr("alternatives")?.optJSONObject(0)
                val transcript = alt?.str("transcript")?.trim().orEmpty()
                val conf = (alt?.optDouble("confidence", 1.0) ?: 1.0).toFloat()
                val isFinal = json.bool("is_final") ?: false
                val speechFinal = json.bool("speech_final") ?: false
                main.post { results(transcript, conf, isFinal, speechFinal) }
            }
            "UtteranceEnd" -> main.post { endTurn() }
            "Error" -> Log.w("nova: $text")
        }
    }

    private fun results(transcript: String, conf: Float, isFinal: Boolean, speechFinal: Boolean) {
        if (transcript.isNotEmpty()) {
            if (isFinal) spoken.final(transcript) else spoken.interim(transcript)
            val text = spoken.text
            // The turn's confidence is the lowest of its finals; a muffled echo scores low.
            confidence = if (!turnOpen) conf else if (isFinal) minOf(confidence, conf) else confidence
            if (!turnOpen) {
                turnOpen = true
                listener?.onTurn(Ears.Turn.START, text, turnIndex, conf)
            } else {
                listener?.onTurn(Ears.Turn.UPDATE, text, turnIndex, conf)
            }
        } else if (isFinal) {
            spoken.utteranceEnd()
        }
        if (speechFinal) endTurn()
    }

    private fun endTurn() {
        if (!turnOpen) return
        val text = spoken.committed.ifEmpty { spoken.text }
        val conf = confidence
        spoken.clear()
        turnOpen = false
        confidence = 1f
        listener?.onTurn(Ears.Turn.END, text, turnIndex, conf)
        turnIndex++
    }
}
