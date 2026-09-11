package io.github.kasecrab.razorback.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import io.github.kasecrab.razorback.core.Http
import io.github.kasecrab.razorback.core.HttpException
import java.io.File
import java.net.URLEncoder
import kotlin.math.sqrt
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

    /** The voice now loading or playing, or null. */
    var playing: String? = null
        private set

    /** True while [playing] is still being fetched. */
    val loading: Boolean get() = playing != null && track == null

    var onChanged: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    /** Media volume is off, so the sample would be silent; the system volume panel is up. */
    var onMuted: (() -> Unit)? = null

    private var track: AudioTrack? = null
    private var pcm: ByteArray? = null
    private var job: Job? = null
    private val main = Handler(Looper.getMainLooper())
    private val finish = Runnable { stop() }

    fun toggle(voice: Voice, scope: CoroutineScope) {
        if (playing == voice.id) stop() else play(voice, scope)
    }

    /** Starts [voice]'s sample, replacing whatever was playing. */
    fun play(voice: Voice, scope: CoroutineScope) {
        stop()
        val apiKey = key()
        if (apiKey == null) {
            onError?.invoke("Add a Deepgram key first")
            return
        }
        playing = voice.id
        onChanged?.invoke()
        warnIfMuted()
        job = scope.launch {
            val data = try {
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
            start(data, voice.id)
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
        pcm = null
        if (playing != null) {
            playing = null
            onChanged?.invoke()
        }
    }

    /** Loudness of the sample at the point now being heard, 0..1, so a pulse can follow the voice. */
    fun level(): Float {
        val t = track ?: return 0f
        val data = pcm ?: return 0f
        val at = t.playbackHeadPosition * 2
        if (at <= 0 || at >= data.size) return 0f
        val end = minOf(data.size, at + WINDOW_BYTES)
        var sum = 0.0
        var n = 0
        var i = at and 1.inv()
        while (i + 1 < end) {
            val s = ((data[i].toInt() and 0xFF) or (data[i + 1].toInt() shl 8)).toShort().toInt()
            sum += s.toDouble() * s
            i += 2
            n++
        }
        if (n == 0) return 0f
        return (sqrt(sum / n).toFloat() / FULL_SCALE).coerceIn(0f, 1f)
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

    private fun start(data: ByteArray, id: String) {
        val frames = data.size / 2
        if (frames == 0) {
            stop()
            return
        }
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(Playback.SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
            )
            .setBufferSizeInBytes(data.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        // A static track reports STATE_NO_STATIC_DATA until its buffer is written; only
        // STATE_UNINITIALIZED means the track could not be made.
        if (t.state == AudioTrack.STATE_UNINITIALIZED || t.write(data, 0, data.size) < data.size) {
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
        pcm = data
        t.play()
        onChanged?.invoke()
        // The end marker is not delivered on every device, so the sample's own length ends it too.
        main.postDelayed(finish, frames * 1000L / Playback.SAMPLE_RATE + 200L)
    }

    /** Samples play at media volume; when that is off, show the volume panel instead of silence. */
    private fun warnIfMuted() {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        val stream = AudioManager.STREAM_MUSIC
        if (!am.isStreamMute(stream) && am.getStreamVolume(stream) > 0) return
        try {
            am.adjustStreamVolume(stream, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
        } catch (_: SecurityException) {
        }
        onMuted?.invoke()
    }

    private companion object {
        /** 40 ms of 24 kHz mono PCM16. */
        const val WINDOW_BYTES = 1920
        const val FULL_SCALE = 9000f
    }
}
