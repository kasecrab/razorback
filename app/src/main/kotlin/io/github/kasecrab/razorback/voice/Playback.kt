package io.github.kasecrab.razorback.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import io.github.kasecrab.razorback.core.Log
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

/**
 * Speaker output for 24 kHz mono PCM16 arriving in bursts. Chunks queue in a ring; the
 * play thread waits for a small cushion before starting so the first words do not stutter.
 * [clear] drops everything at once for barge-in.
 */
class Playback(private val onDrained: () -> Unit) {

    val level = AtomicInteger(0)

    private val ring = ByteArray(RING_BYTES)
    private var head = 0
    private var size = 0
    private val lock = Object()
    private var endMarked = false
    @Volatile private var running = false
    @Volatile private var playing = false
    private var thread: Thread? = null
    private var track: AudioTrack? = null
    private var generation = 0

    val isPlaying: Boolean get() = playing

    fun start() {
        if (running) return
        val min = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(min, BYTES_PER_MS * 200))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        if (t.state != AudioTrack.STATE_INITIALIZED) {
            Log.w("audio track failed to initialise")
            t.release()
            return
        }
        track = t
        running = true
        val th = Thread({ loop(t) }, "playback")
        thread = th
        th.start()
    }

    fun enqueue(data: ByteArray, offset: Int = 0, length: Int = data.size - offset) {
        synchronized(lock) {
            var len = length
            var off = offset
            if (len > RING_BYTES) {
                off += len - RING_BYTES
                len = RING_BYTES
            }
            // Overflow drops the oldest audio; better a skip than an ever-growing delay.
            val free = RING_BYTES - size
            if (len > free) {
                val drop = len - free
                head = (head + drop) % RING_BYTES
                size -= drop
            }
            var tail = (head + size) % RING_BYTES
            var remaining = len
            while (remaining > 0) {
                val n = minOf(remaining, RING_BYTES - tail)
                System.arraycopy(data, off, ring, tail, n)
                tail = (tail + n) % RING_BYTES
                off += n
                remaining -= n
            }
            size += len
            endMarked = false
            lock.notifyAll()
        }
    }

    /** No more audio is coming for now; [onDrained] fires once the ring empties. */
    fun markEnd() {
        synchronized(lock) {
            endMarked = true
            lock.notifyAll()
        }
    }

    /** Stop speaking right now and forget what was queued. */
    fun clear() {
        synchronized(lock) {
            head = 0
            size = 0
            endMarked = false
            generation++
            lock.notifyAll()
        }
        track?.let {
            try {
                it.pause()
                it.flush()
            } catch (_: IllegalStateException) {
            }
        }
        playing = false
        level.set(0)
    }

    fun stop() {
        if (!running) return
        running = false
        synchronized(lock) { lock.notifyAll() }
        try {
            thread?.join(300)
        } catch (_: InterruptedException) {
        }
        thread = null
        track?.let {
            try {
                it.pause()
                it.flush()
                it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        track = null
        playing = false
        level.set(0)
    }

    private fun loop(t: AudioTrack) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val chunk = ByteArray(BYTES_PER_MS * 40)
        var gen: Int
        while (running) {
            var n: Int
            var drained = false
            synchronized(lock) {
                while (running && (size == 0 || (!playing && size < BYTES_PER_MS * PREBUFFER_MS && !endMarked))) {
                    if (size == 0 && endMarked && playing) {
                        drained = true
                        endMarked = false
                        break
                    }
                    lock.wait(200)
                }
                if (!running) return
                gen = generation
                n = if (drained) 0 else minOf(chunk.size, size)
                var copied = 0
                while (copied < n) {
                    val len = minOf(n - copied, RING_BYTES - head)
                    System.arraycopy(ring, head, chunk, copied, len)
                    head = (head + len) % RING_BYTES
                    copied += len
                }
                size -= n
            }
            if (drained) {
                waitForTrack(t)
                playing = false
                level.set(0)
                onDrained()
                continue
            }
            if (!playing) {
                try {
                    t.play()
                } catch (e: IllegalStateException) {
                    Log.w("track play failed", e)
                    continue
                }
                playing = true
            }
            level.set(rms(chunk, n))
            var written = 0
            while (written < n && running) {
                val w = t.write(chunk, written, n - written, AudioTrack.WRITE_BLOCKING)
                if (w < 0) break
                written += w
                if (gen != generation) break
            }
        }
    }

    /** Let the last buffered milliseconds play out before reporting silence. */
    private fun waitForTrack(t: AudioTrack) {
        try {
            Thread.sleep(120)
        } catch (_: InterruptedException) {
        }
    }

    private fun rms(b: ByteArray, len: Int): Int {
        var sum = 0.0
        var i = 0
        while (i + 1 < len) {
            val s = ((b[i + 1].toInt() shl 8) or (b[i].toInt() and 0xFF)).toShort().toInt()
            sum += s.toDouble() * s
            i += 2
        }
        val n = len / 2
        if (n == 0) return 0
        return (sqrt(sum / n) / 32768.0 * 1000).toInt()
    }

    companion object {
        const val SAMPLE_RATE = 24000
        const val BYTES_PER_MS = SAMPLE_RATE * 2 / 1000
        const val PREBUFFER_MS = 120
        const val RING_BYTES = 512 * 1024
    }
}
