package io.github.kasecrab.razorback.remote

/**
 * How far each machine link has been read.
 *
 * The relay decides what it hands back, and it is trusted with none of it, so a link this
 * phone has already read frames of must not start again from nothing the next time it is
 * seen: a relay that wanted to could then play those frames a second time and the opener
 * would take every one of them. The number reached is put away whenever this phone stops
 * reading a link — the machine took a new one, or the socket went down — and picked up
 * again the moment that link comes round.
 *
 * A link is remembered by its bytes rather than by however a frame happened to spell them,
 * so one link is one window and not two. Memory is bounded: past [KEPT] links the least
 * recently seen is forgotten, one at a time rather than all of them at once.
 */
internal class ReplayWindows {

    private val seen = object : LinkedHashMap<String, Long>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > KEPT
    }

    /** Put away how far [link] has been read. */
    fun keep(link: ByteArray, seq: Long) {
        seen[Crypto.hex(link)] = seq
    }

    /** How far [link] was read, or nought if this is the first sight of it. */
    fun resume(link: ByteArray): Long = seen[Crypto.hex(link)] ?: 0L

    private companion object {
        const val KEPT = 64
    }
}
