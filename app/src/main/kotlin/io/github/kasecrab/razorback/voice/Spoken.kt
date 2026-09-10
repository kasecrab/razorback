package io.github.kasecrab.razorback.voice

/**
 * What has been said (final) plus what is being said (interim guess). Interims replace
 * the guess wholesale; finals are joined with a space unless they start with punctuation.
 * After the mic is released, a straggling final still lands for a short settle window.
 */
class Spoken {
    private val said = StringBuilder()
    private var guess = ""
    private var releasedAt = 0L

    val text: String get() = join(said.toString(), guess)
    val committed: String get() = said.toString()
    val pending: String get() = guess

    fun interim(t: String) {
        guess = t.trim()
    }

    fun final(t: String) {
        val add = t.trim()
        if (add.isEmpty()) return
        if (said.isNotEmpty() && !startsWithPunct(add)) said.append(' ')
        said.append(add)
        guess = ""
    }

    fun utteranceEnd() {
        guess = ""
    }

    fun release(now: Long) {
        releasedAt = now
    }

    /** True when nothing more is expected: a final arrived after release, or the window passed. */
    fun settled(now: Long, listening: Boolean): Boolean {
        if (listening) return false
        if (releasedAt == 0L) return said.isNotEmpty() || guess.isNotEmpty()
        return (said.isNotEmpty() && guess.isEmpty()) || now - releasedAt > SETTLE_MS
    }

    fun clear() {
        said.setLength(0)
        guess = ""
        releasedAt = 0L
    }

    private fun join(a: String, b: String): String = when {
        b.isEmpty() -> a
        a.isEmpty() -> b
        startsWithPunct(b) -> a + b
        else -> "$a $b"
    }

    private fun startsWithPunct(s: String): Boolean = s.isNotEmpty() && s[0] in ",.!?;:"

    companion object {
        const val SETTLE_MS = 1200L
    }
}
