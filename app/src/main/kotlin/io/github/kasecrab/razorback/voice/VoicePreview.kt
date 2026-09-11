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
import io.github.kasecrab.razorback.core.Log
import java.io.File
import java.net.URLEncoder
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lets a voice be chosen by ear. Deepgram's own recording of the voice is fetched, the
 * first seconds of it, and kept in the cache; a voice without one says a sentence
 * through Aura's plain request endpoint instead.
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
    private var clip: Wav.Clip? = null
    private var job: Job? = null
    /** Bumped by every play and stop, so a fetch that finishes late knows it is no longer wanted. */
    private var generation = 0
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
        val gen = ++generation
        playing = voice.id
        onChanged?.invoke()
        warnIfMuted()
        job = scope.launch {
            val data = try {
                withContext(Dispatchers.IO) { fetch(apiKey, voice) ?: throw IllegalStateException("No sample for this voice") }
            } catch (e: CancellationException) {
                // Swiped on before the sample arrived: nothing to report.
                return@launch
            } catch (e: Exception) {
                if (gen == generation) {
                    playing = null
                    onChanged?.invoke()
                    onError?.invoke(e.message ?: "Could not fetch the sample")
                }
                return@launch
            }
            if (gen == generation) start(data, gen)
        }
    }

    fun stop() {
        generation++
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
        clip = null
        if (playing != null) {
            playing = null
            onChanged?.invoke()
        }
    }

    /** Loudness of the sample at the point now being heard, 0..1, so a pulse can follow the voice. */
    fun level(): Float {
        val t = track ?: return 0f
        val c = clip ?: return 0f
        val data = c.bytes
        val frameBytes = 2 * c.channels
        val at = c.offset + t.playbackHeadPosition * frameBytes
        val stop = c.offset + c.length
        if (at <= c.offset || at >= stop) return 0f
        val end = minOf(stop, at + c.rate * frameBytes / 25)
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

    private fun fetch(apiKey: String, voice: Voice): Wav.Clip? {
        val dir = File(context.cacheDir, "voices")
        val wav = File(dir, voice.id + ".wav")
        if (wav.exists() && wav.length() > 0) Wav.parse(wav.readBytes())?.let { return it }
        val url = voice.sample
        if (url != null) {
            try {
                val bytes = download(url)
                val parsed = Wav.parse(bytes)
                if (parsed != null) {
                    dir.mkdirs()
                    wav.writeBytes(bytes)
                    return parsed
                }
            } catch (e: Exception) {
                Log.w("voice sample download failed", e)
            }
        }
        val pcm = File(dir, voice.id + ".pcm")
        val raw = if (pcm.exists() && pcm.length() > 0) pcm.readBytes() else synthesize(apiKey, voice).also {
            dir.mkdirs()
            pcm.writeBytes(it)
        }
        return Wav.Clip(raw, 0, raw.size, Playback.SAMPLE_RATE, 1)
    }

    /** The first [CLIP_BYTES] of Deepgram's recording; servers that ignore the range send it all, which is fine. */
    private fun download(url: String): ByteArray {
        val conn = Http.open(url, "GET", mapOf("Range" to "bytes=0-$CLIP_BYTES", "Accept" to "audio/wav, */*"))
        try {
            val status = conn.responseCode
            if (status >= 400) throw HttpException(status, "HTTP $status")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    /** What a voice says when Deepgram has no recording of it, in its own language. */
    private fun sentence(v: Voice): String = when (v.language) {
        "es" -> "Hola, soy ${v.name}. Así suena mi voz."
        "nl" -> "Hoi, ik ben ${v.name}. Zo klinkt mijn stem."
        "fr" -> "Bonjour, je suis ${v.name}. Voici ma voix."
        "de" -> "Hallo, ich bin ${v.name}. So klingt meine Stimme."
        "it" -> "Ciao, sono ${v.name}. Questa è la mia voce."
        "ja" -> "こんにちは、${v.name}です。これが私の声です。"
        else -> "Hi, I'm ${v.name}. This is what I sound like."
    }

    private fun synthesize(apiKey: String, voice: Voice): ByteArray {
        val url = "https://api.deepgram.com/v1/speak?model=" + URLEncoder.encode(voice.id, "UTF-8") +
            "&encoding=linear16&sample_rate=" + Playback.SAMPLE_RATE + "&container=none"
        val conn = Http.open(url, "POST", mapOf("Authorization" to "Token $apiKey"))
        try {
            val body = org.json.JSONObject().put("text", sentence(voice)).toString().toByteArray(Charsets.UTF_8)
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
            val status = conn.responseCode
            if (status >= 400) throw HttpException(status, Http.errorMessage(conn, status))
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun start(c: Wav.Clip, gen: Int) {
        val frames = c.frames
        if (frames == 0) {
            stop()
            return
        }
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(c.rate)
                    .setChannelMask(if (c.channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO).build(),
            )
            .setBufferSizeInBytes(c.length)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        // A static track reports STATE_NO_STATIC_DATA until its buffer is written; only
        // STATE_UNINITIALIZED means the track could not be made.
        if (t.state == AudioTrack.STATE_UNINITIALIZED || t.write(c.bytes, c.offset, c.length) < c.length) {
            t.release()
            stop()
            return
        }
        t.setNotificationMarkerPosition(frames)
        t.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(track: AudioTrack) {
                if (gen == generation) main.post(finish)
            }

            override fun onPeriodicNotification(track: AudioTrack) {}
        })
        track = t
        clip = c
        t.play()
        onChanged?.invoke()
        // The end marker is not delivered on every device, so the sample's own length ends it too.
        main.postDelayed(finish, frames * 1000L / c.rate + 200L)
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
        const val FULL_SCALE = 9000f
        /** Eight seconds of 24 kHz mono PCM16 plus a header: enough to judge a voice. */
        const val CLIP_BYTES = 44 + 8 * 48000
    }
}
