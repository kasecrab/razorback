package io.github.kasecrab.razorback.voice

/**
 * Cuts a token stream into sentences worth speaking as soon as they are complete, so the
 * first audio starts before the model has finished. A fenced code block is one chunk;
 * decimals and common abbreviations do not end a sentence.
 */
class SentenceChunker(private val onSentence: (String) -> Unit) {

    private val buf = StringBuilder()

    fun push(text: String) {
        buf.append(text)
        drain(force = false)
    }

    /** End of the stream: whatever is left goes out. */
    fun flush() {
        drain(force = true)
        val rest = buf.toString().trim()
        buf.setLength(0)
        if (rest.isNotEmpty()) onSentence(rest)
    }

    fun reset() {
        buf.setLength(0)
    }

    private fun drain(force: Boolean) {
        while (true) {
            val cut = findCut() ?: break
            val sentence = buf.substring(0, cut).trim()
            buf.delete(0, cut)
            if (sentence.isNotEmpty()) onSentence(sentence)
        }
        if (!force && buf.length > HARD_LIMIT && !startsWithFence()) {
            val at = lastSoftBreak(buf, HARD_LIMIT)
            val sentence = buf.substring(0, at).trim()
            buf.delete(0, at)
            if (sentence.isNotEmpty()) onSentence(sentence)
        }
    }

    private fun startsWithFence(): Boolean {
        var i = 0
        while (i < buf.length && buf[i].isWhitespace()) i++
        return buf.startsWith(FENCE, i)
    }

    /** Index just after a complete sentence, or null when nothing is complete yet. */
    private fun findCut(): Int? {
        val n = buf.length
        var lead = 0
        while (lead < n && buf[lead].isWhitespace()) lead++
        if (buf.startsWith(FENCE, lead)) {
            // A whole code block is one chunk; wait for its closing fence and newline.
            val close = buf.indexOf(FENCE, lead + 3)
            if (close < 0) return null
            val nl = buf.indexOf('\n', close + 3)
            return if (nl < 0) null else nl + 1
        }
        val fenceAt = buf.indexOf(FENCE, lead)
        var i = 0
        val limit = if (fenceAt >= 0) fenceAt else n
        while (i < limit) {
            val c = buf[i]
            if (c == '\n') {
                if (i >= MIN_LEN || buf.substring(0, i).isBlank()) return i + 1
                i++
                continue
            }
            if (c == '。' || c == '！' || c == '？') return i + 1
            if (c == '.' || c == '!' || c == '?') {
                if (i + 1 >= n) return null
                val next = buf[i + 1]
                if (next == '.' || next == '!' || next == '?') {
                    i++
                    continue
                }
                if (c == '.' && (next.isDigit() || isAbbreviation(i))) {
                    i++
                    continue
                }
                if (next.isWhitespace() || next == '"' || next == '”' || next == ')') {
                    var end = i + 1
                    while (end < n && (buf[end] == '"' || buf[end] == '”' || buf[end] == ')')) end++
                    if (end >= MIN_LEN) return end
                }
            }
            i++
        }
        // Text before a fence goes out on its own so the code block is not read mid-sentence.
        if (fenceAt > 0 && !buf.substring(0, fenceAt).isBlank()) return fenceAt
        return null
    }

    private fun isAbbreviation(dot: Int): Boolean {
        var start = dot
        while (start > 0 && (buf[start - 1].isLetter() || buf[start - 1] == '.')) start--
        if (start == dot) return false
        val word = buf.substring(start, dot).trimStart('.').lowercase()
        if (word.isEmpty()) return false
        return word in ABBREVIATIONS || (word.length == 1 && buf[dot - 1].isUpperCase())
    }

    private fun lastSoftBreak(s: CharSequence, limit: Int): Int {
        for (i in limit downTo limit / 2) if (s[i] == ',' || s[i] == ';' || s[i] == ':') return i + 1
        for (i in limit downTo limit / 2) if (s[i] == ' ') return i + 1
        return limit
    }

    private companion object {
        const val FENCE = "```"
        const val MIN_LEN = 15
        const val HARD_LIMIT = 240
        val ABBREVIATIONS = setOf(
            "e.g", "i.e", "etc", "vs", "dr", "mr", "mrs", "ms", "prof", "sr", "jr", "st", "no", "fig",
            "approx", "dept", "inc", "ltd", "co", "eg", "ie", "u.s", "u.k", "p.m", "a.m",
        )
    }
}
