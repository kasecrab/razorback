package io.github.kasecrab.razorback.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import io.github.kasecrab.razorback.core.Http
import io.github.kasecrab.razorback.core.HttpException
import java.io.File
import java.net.URLEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Says one sentence in a voice so it can be chosen by ear. The audio is fetched once
 * over Aura's plain request endpoint and kept in the cache, so a second listen is free.
 */
class VoicePreview(private val context: Context, private val key: () -> String?) {

    /** The voice now playing, or null. */
    var playing: String? = null
        private set
    var onChanged: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private var track: AudioTrack? = null
    private var job: Job? = null
    private val main = Handler(Looper.getMainLooper())
    private val finish = Runnable { stop() }

    fun toggle(voice: Voice, scope: CoroutineScope) {
        if (playing == voice.id) {
            stop()
            return
        }
        stop()
        val apiKey = key()
        if (apiKey == null) {
            onError?.invoke("Add a Deepgram key first")
            return
        }
        playing = voice.id
        onChanged?.invoke()
        job = scope.launch {
            val pcm = try {
                withContext(Dispatchers.IO) { fetch(apiKey, voice) }
            } catch (e: Exception) {
                if (playing == voice.id) {
                    playing = null
                    onChanged?.invoke()
                    onError?.invoke(e.message ?: "Could not fetch the sample")
                }
                return@launch
            }
            if (playing != voice.id) return@launch
            play(pcm, voice.id)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        main.removeCallbacks(finish)
        track?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        track = null
        if (playing != null) {
            playing = null
            onChanged?.invoke()
        }
    }

    private fun fetch(apiKey: String, voice: Voice): ByteArray {
        val dir = File(context.cacheDir, "voices")
        val file = File(dir, voice.id + ".pcm")
        if (file.exists() && file.length() > 0) return file.readBytes()
        val url = "https://api.deepgram.com/v1/speak?model=" + URLEncoder.encode(voice.id, "UTF-8") +
            "&encoding=linear16&sample_rate=" + Playback.SAMPLE_RATE + "&container=none"
        val conn = Http.open(url, "POST", mapOf("Authorization" to "Token $apiKey"))
        try {
            val body = org.json.JSONObject().put("text", Voices.sample(voice)).toString().toByteArray(Charsets.UTF_8)
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
            val status = conn.responseCode
            if (status >= 400) throw HttpException(status, Http.errorMessage(conn, status))
            val bytes = conn.inputStream.use { it.readBytes() }
            dir.mkdirs()
            file.writeBytes(bytes)
            return bytes
        } finally {
            conn.disconnect()
        }
    }

    private fun play(pcm: ByteArray, id: String) {
        val frames = pcm.size / 2
        if (frames == 0) {
            stop()
            return
        }
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(Playback.SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
            )
            .setBufferSizeInBytes(pcm.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        // A static track reports STATE_NO_STATIC_DATA until its buffer is written; only
        // STATE_UNINITIALIZED means the track could not be made.
        if (t.state == AudioTrack.STATE_UNINITIALIZED || t.write(pcm, 0, pcm.size) < pcm.size) {
            t.release()
            stop()
            return
        }
        t.setNotificationMarkerPosition(frames)
        t.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(track: AudioTrack) {
                if (playing == id) main.post(finish)
            }

            override fun onPeriodicNotification(track: AudioTrack) {}
        })
        track = t
        t.play()
        // The end marker is not delivered on every device, so the sample's own length ends it too.
        main.postDelayed(finish, frames * 1000L / Playback.SAMPLE_RATE + 200L)
    }

}
