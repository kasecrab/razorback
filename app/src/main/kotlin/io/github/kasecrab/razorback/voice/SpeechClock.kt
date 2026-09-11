package io.github.kasecrab.razorback.voice

/**
 * Where the voice is in the text it is reading. Aura returns no word timings, but it does
 * return the audio for each run of text between one Flush and the next, so each run's
 * length in bytes is known once it is flushed. Within a run the time is shared out over
 * the words by their length and the pauses after them. The play head resynchronises at
 * every run, so an estimate never drifts further than the run it is in.
 *
 * A run still arriving is guessed from its length at the pace the voice has shown so far,
 * and the guess only ever moves the highlight forward: when it was ahead, the highlight
 * waits for the voice to catch up rather than stepping back and forth.
 *
 * [audio] is called from the socket thread; everything else from the main thread.
 */
class SpeechClock {

    private class Sentence(val wordOffset: Int, val wordCount: Int, val chars: Int, val cumulative: FloatArray) {
        @Volatile var bytes = 0L
        @Volatile var flushed = false
    }

    private val sentences = ArrayList<Sentence>(16)
    private var open = 0
    private var flushedCount = 0
    /** Audio that arrived before any run was registered for it; it belongs to the next one. */
    private var orphanBytes = 0L
    /** Bytes of audio per character, from the runs already heard, weighted towards the latest. */
    private var paceBytes = 0f
    private var paceChars = 0f

    /** Words in every sentence so far, counted the way the transcript counts them: by whitespace. */
    var wordTotal = 0
        private set

    val sentenceCount: Int get() = sentences.size
    val allFlushed: Boolean get() = flushedCount == sentences.size

    @Volatile var totalBytes = 0L
        private set

    /** Outputs of [seek]: the word being spoken (across all sentences) and how far through it. */
    var word = -1
        private set
    var fraction = 0f
        private set

    /** Forgets the reply but keeps the pace the voice has shown, which is the same voice at the same speed. */
    fun reset() {
        synchronized(this) {
            sentences.clear()
            open = 0
            flushedCount = 0
            orphanBytes = 0L
            wordTotal = 0
            totalBytes = 0L
            word = -1
            fraction = 0f
        }
    }

    /** A sentence has been handed to the voice; its audio is the next to arrive. */
    fun sentence(text: String) {
        val weights = ArrayList<Float>(16)
        var i = 0
        val n = text.length
        var chars = 0
        while (i < n) {
            while (i < n && text[i].isWhitespace()) i++
            if (i >= n) break
            var letters = 0
            var pause = 0f
            while (i < n && !text[i].isWhitespace()) {
                val c = text[i]
                if (c.isLetterOrDigit()) letters++
                else if (c == '.' || c == '!' || c == '?' || c == '。' || c == '！' || c == '？') pause = maxOf(pause, 1.2f)
                else if (c == ',' || c == ';' || c == ':') pause = maxOf(pause, 0.6f)
                i++
                chars++
            }
            weights.add(letters + 0.35f + pause)
        }
        val cumulative = FloatArray(weights.size)
        var sum = 0f
        for (w in weights) sum += w
        var acc = 0f
        for (k in weights.indices) {
            acc += weights[k]
            cumulative[k] = if (sum > 0f) acc / sum else 1f
        }
        synchronized(this) {
            val s = Sentence(wordTotal, weights.size, chars, cumulative)
            s.bytes = orphanBytes
            orphanBytes = 0L
            sentences.add(s)
            wordTotal += weights.size
        }
    }

    /** Audio bytes arrived; they belong to the oldest run not yet flushed. */
    fun audio(bytes: Int) {
        synchronized(this) {
            totalBytes += bytes
            val s = sentences.getOrNull(open)
            if (s == null) orphanBytes += bytes else s.bytes += bytes
        }
    }

    /** The voice finished one run's audio. */
    fun flushed() {
        synchronized(this) {
            val s = sentences.getOrNull(open) ?: return
            s.flushed = true
            flushedCount++
            open++
            if (s.chars > 0 && s.bytes > 0L) {
                paceBytes = paceBytes * PACE_MEMORY + s.bytes
                paceChars = paceChars * PACE_MEMORY + s.chars
            }
        }
    }

    /** Under the lock: bytes a run of [chars] characters should take, from what has been heard so far. */
    private fun guess(chars: Int): Long {
        val perChar = if (paceChars > 0f) paceBytes / paceChars else BYTES_PER_CHAR.toFloat()
        return (chars * perChar).toLong()
    }

    /**
     * Point at the word being spoken when [playedBytes] of this reply's audio have been
     * heard. Returns true when the word or the position within it moved. Within a reply
     * the position never moves back: a guess that ran ahead waits to be caught up with.
     */
    fun seek(playedBytes: Long): Boolean {
        var newWord = -1
        var newFraction = 0f
        synchronized(this) {
            var start = 0L
            var found = false
            for (s in sentences) {
                val len = if (s.flushed) s.bytes else maxOf(s.bytes, guess(s.chars))
                if (playedBytes < start + len || s === sentences.last()) {
                    if (s.wordCount == 0) {
                        newWord = s.wordOffset
                        break
                    }
                    val within = if (len <= 0) 1f else ((playedBytes - start).toFloat() / len).coerceIn(0f, 1f)
                    var k = 0
                    while (k < s.cumulative.size - 1 && s.cumulative[k] <= within) k++
                    val lo = if (k == 0) 0f else s.cumulative[k - 1]
                    val hi = s.cumulative[k]
                    newWord = s.wordOffset + k
                    newFraction = if (hi > lo) ((within - lo) / (hi - lo)).coerceIn(0f, 1f) else 1f
                    found = true
                    break
                }
                start += len
            }
            if (!found && sentences.isEmpty()) newWord = -1
        }
        if (newWord < word || (newWord == word && newFraction < fraction)) return false
        val changed = newWord != word || newFraction != fraction
        word = newWord
        fraction = newFraction
        return changed
    }

    private companion object {
        /** Until a run has been heard: 24 kHz 16-bit speech runs near sixteen characters a second. */
        const val BYTES_PER_CHAR = 3000L
        /** How much of the earlier runs' pace survives each new one; the latest run counts most. */
        const val PACE_MEMORY = 0.6f
    }
}
