package io.github.kasecrab.razorback.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import io.github.kasecrab.razorback.core.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

/**
 * The microphone as 16 kHz mono PCM16 in 80 ms chunks, read on its own urgent thread
 * into one reusable buffer. [level] is the latest RMS in thousandths for the meters.
 */
class MicCapture(
    private val source: Int = MediaRecorder.AudioSource.VOICE_RECOGNITION,
    private val onChunk: (ByteArray, Int) -> Unit,
) {
    val level = AtomicInteger(0)
    val muted = AtomicBoolean(false)

    @Volatile private var running = false
    private var thread: Thread? = null
    private var record: AudioRecord? = null

    val isRunning: Boolean get() = running

    /** Needs RECORD_AUDIO; returns false when the device refuses to open the mic. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return false
        val rec = try {
            AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 4, CHUNK_BYTES * 8))
        } catch (e: Exception) {
            Log.w("mic open failed", e)
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        if (source == MediaRecorder.AudioSource.VOICE_COMMUNICATION) {
            if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(rec.audioSessionId)?.enabled = true
            if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(rec.audioSessionId)?.enabled = true
        }
        record = rec
        running = true
        val t = Thread({ loop(rec) }, "mic")
        thread = t
        t.start()
        return true
    }

    fun stop() {
        if (!running) return
        running = false
        try {
            thread?.join(300)
        } catch (_: InterruptedException) {
        }
        thread = null
        record?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        record = null
        level.set(0)
    }

    private fun loop(rec: AudioRecord) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val buf = ByteArray(CHUNK_BYTES)
        var chunks = 0
        try {
            rec.startRecording()
        } catch (e: IllegalStateException) {
            Log.w("mic start failed", e)
            running = false
            return
        }
        while (running) {
            var filled = 0
            while (filled < CHUNK_BYTES && running) {
                val n = rec.read(buf, filled, CHUNK_BYTES - filled)
                if (n < 0) {
                    running = false
                    break
                }
                filled += n
            }
            if (!running) break
            if (muted.get()) {
                java.util.Arrays.fill(buf, 0)
                level.set(0)
            } else {
                level.set(rms(buf, filled))
            }
            if (Log.ON && ++chunks % 25 == 0) Log.d { "mic: level ${level.get()} of 1000 after ${chunks * CHUNK_MS / 1000} s" }
            onChunk(buf, filled)
        }
    }

    private fun rms(b: ByteArray, len: Int): Int {
        var sum = 0.0
        var i = 0
        val n = len / 2
        while (i + 1 < len) {
            val s = ((b[i + 1].toInt() shl 8) or (b[i].toInt() and 0xFF)).toShort().toInt()
            sum += s.toDouble() * s
            i += 2
        }
        if (n == 0) return 0
        return (sqrt(sum / n) / 32768.0 * 1000).toInt()
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHUNK_MS = 80
        const val CHUNK_BYTES = SAMPLE_RATE * 2 * CHUNK_MS / 1000
    }
}
