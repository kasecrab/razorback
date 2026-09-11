package io.github.kasecrab.razorback.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.PlaybackParams
import android.os.Process
import io.github.kasecrab.razorback.core.Log
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

/**
 * Speaker output for 24 kHz mono PCM16 arriving in bursts. Chunks queue in a ring that
 * grows when a long reply arrives faster than it can be played, because dropping any of
 * it would drop words. The play thread waits for a cushion before starting so the first
 * words do not stutter; on a connection where the audio then trickles in late, every
 * time the queue runs dry the cushion doubles before playing resumes, trading many short
 * stutters for one pause, and it eases back after replies that played clean.
 * [clear] drops everything at once for barge-in.
 */
class Playback(
    /** Whether audio is still owed for what has been asked of the voice; a dry queue otherwise is a wait, not a stall. */
    private val expecting: () -> Boolean = { true },
    private val onDrained: () -> Unit,
) {

    val level = AtomicInteger(0)

    /** Bytes accepted since the track last started from silence; see [playedBytes]. */
    val enqueuedBytes = AtomicLong(0)

    private var ring = ByteArray(RING_BYTES)
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

    /** Times the speaker went quiet mid-speech waiting for audio; a diagnostic. */
    @Volatile var underruns = 0
        private set

    /** Audio held back before playing starts or resumes, in listening time; grows with the jitter seen. */
    @Volatile private var cushionMs = CUSHION_MS
    private var underrunsThisRun = 0
    /** How many times faster than sent the audio is played; the cushion is that much bigger in bytes. */
    @Volatile private var stretch = 1f

    /** [stretch] plays the audio that many times faster at the same pitch; 1 leaves it as sent. */
    fun start(stretch: Float = 1f) {
        if (running) return
        val min = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val stretching = stretch > 1.001f
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
            // A stretched track eats source audio faster, so it gets room for up to four times the pace.
            .setBufferSizeInBytes(maxOf(min, BYTES_PER_MS * 200) * (if (stretching) 4 else 1))
            .setTransferMode(AudioTrack.MODE_STREAM)
            // The fast mixer cannot time-stretch, so a stretched track takes the ordinary path.
            .setPerformanceMode(if (stretching) AudioTrack.PERFORMANCE_MODE_NONE else AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        if (t.state != AudioTrack.STATE_INITIALIZED) {
            Log.w("audio track failed to initialise")
            t.release()
            return
        }
        if (stretching) applyStretch(t, stretch)
        this.stretch = if (stretching) stretch else 1f
        track = t
        headBase = 0L
        enqueuedBytes.set(0)
        running = true
        val th = Thread({ loop(t) }, "playback")
        thread = th
        th.start()
    }

    /** Changes the pace of what is already playing; a fast track cannot stretch and keeps its pace. */
    fun setStretch(stretch: Float) {
        val s = stretch.coerceIn(1f, 4f)
        track?.let { applyStretch(it, s) }
        this.stretch = s
    }

    private fun applyStretch(t: AudioTrack, stretch: Float) {
        try {
            t.playbackParams = PlaybackParams().setSpeed(stretch).setPitch(1f).setAudioFallbackMode(PlaybackParams.AUDIO_FALLBACK_MODE_DEFAULT)
        } catch (e: IllegalArgumentException) {
            Log.w("time stretch refused: ${e.message}")
        }
    }

    fun enqueue(data: ByteArray, offset: Int = 0, length: Int = data.size - offset) {
        synchronized(lock) {
            var len = length
            var off = offset
            if (len > ring.size - size) grow(size + len)
            if (len > ring.size - size) {
                // Past the cap even so; only then does the oldest audio go.
                val drop = len - (ring.size - size)
                head = (head + drop) % ring.size
                size -= drop
            }
            var tail = (head + size) % ring.size
            var remaining = len
            while (remaining > 0) {
                val n = minOf(remaining, ring.size - tail)
                System.arraycopy(data, off, ring, tail, n)
                tail = (tail + n) % ring.size
                off += n
                remaining -= n
            }
            size += len
            endMarked = false
            enqueuedBytes.addAndGet(len.toLong())
            lock.notifyAll()
        }
    }

    /**
     * Bytes the speaker has actually presented on the same timeline as [enqueuedBytes]:
     * both count from zero after [start] or [clear], so a reply that began at
     * `enqueuedBytes == n` is `playedBytes() - n` bytes in.
     */
    fun playedBytes(): Long {
        val t = track ?: return 0L
        return maxOf(0L, (rawHead(t) - headBase) * 2L)
    }

    private fun rawHead(t: AudioTrack): Long = try {
        t.playbackHeadPosition.toLong() and 0xFFFFFFFFL
    } catch (_: IllegalStateException) {
        headBase
    }

    /** Whatever the head reads after a flush is the new zero; no reliance on the track resetting it. */
    @Volatile private var headBase = 0L

    /** Under the lock: a bigger ring with the queued audio straightened out at the front. */
    private fun grow(needed: Int) {
        var cap = ring.size
        while (cap < needed && cap < MAX_RING_BYTES) cap *= 2
        if (cap == ring.size) return
        val bigger = ByteArray(cap)
        val first = minOf(size, ring.size - head)
        System.arraycopy(ring, head, bigger, 0, first)
        if (size > first) System.arraycopy(ring, 0, bigger, first, size - first)
        ring = bigger
        head = 0
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
            underrunsThisRun = 0
            lock.notifyAll()
        }
        track?.let {
            try {
                it.pause()
                it.flush()
            } catch (_: IllegalStateException) {
            }
            headBase = rawHead(it)
        }
        enqueuedBytes.set(0)
        playing = false
        level.set(0)
    }

    fun stop() {
        if (!running) return
        running = false
        synchronized(lock) {
            // Whatever was still queued belongs to the session that just ended.
            head = 0
            size = 0
            endMarked = false
            generation++
            lock.notifyAll()
        }
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
        enqueuedBytes.set(0)
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
                // Mid-speech with nothing queued and no end in sight: the audio is late, and the
                // speaker will go quiet until it lands. Counted so a choppy reply can be diagnosed.
                val starved = playing && size == 0 && !endMarked && expecting()
                val starvedAt = if (starved) System.nanoTime() else 0L
                // Once dry, wait for a bigger cushion rather than playing each chunk as it lands.
                val wanted = if (starved) minOf(cushionMs * 2, CUSHION_MAX_MS) else cushionMs
                val need = if (starved || !playing) (BYTES_PER_MS * wanted * stretch).toInt() else 1
                while (running && (size == 0 || (size < need && !endMarked))) {
                    if (size == 0 && endMarked && playing) {
                        drained = true
                        endMarked = false
                        break
                    }
                    lock.wait(200)
                }
                if (starved && running && !drained) {
                    // More audio came: this was a real stall mid-reply, not the wait for the end marker.
                    underruns++
                    underrunsThisRun++
                    cushionMs = wanted
                    Log.d { "playback: queue ran dry for ${(System.nanoTime() - starvedAt) / 1_000_000} ms, cushion now $cushionMs ms" }
                }
                if (!running) return
                gen = generation
                n = if (drained) 0 else minOf(chunk.size, size)
                var copied = 0
                while (copied < n) {
                    val len = minOf(n - copied, ring.size - head)
                    System.arraycopy(ring, head, chunk, copied, len)
                    head = (head + len) % ring.size
                    copied += len
                }
                size -= n
            }
            if (drained) {
                waitForTrack(t)
                playing = false
                level.set(0)
                // A reply that played clean is a sign the connection has settled.
                if (underrunsThisRun == 0) cushionMs = maxOf(CUSHION_MS, cushionMs / 2)
                underrunsThisRun = 0
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
        /** The cushion to start with; the first words wait this long at most on a good connection. */
        const val CUSHION_MS = 200
        const val CUSHION_MAX_MS = 2400
        const val RING_BYTES = 512 * 1024
        /** About six minutes of speech; a reply longer than that is not one anyone waits through. */
        const val MAX_RING_BYTES = 16 * 1024 * 1024
    }
}
