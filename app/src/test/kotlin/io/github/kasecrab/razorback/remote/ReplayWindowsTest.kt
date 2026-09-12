package io.github.kasecrab.razorback.remote

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What keeps a link from being read twice.
 *
 * The relay hands back its log to whoever subscribes, so the number a link was
 * read to has to outlive every reason this phone stops reading it: the machine
 * taking a new link, and the socket going down and being dialled again.
 */
class ReplayWindowsTest {

    private fun link(byte: Int) = ByteArray(Crypto.LINK_BYTES) { byte.toByte() }

    @Test
    fun aLinkComesBackToTheNumberItWasReadTo() {
        val windows = ReplayWindows()
        assertEquals(0L, windows.resume(link(1)))
        windows.keep(link(1), 9)
        assertEquals(9L, windows.resume(link(1)))
        // Another link is its own window and starts where every link starts.
        assertEquals(0L, windows.resume(link(2)))
    }

    @Test
    fun aLinkPutAwayTwiceIsStillOneLink() {
        val windows = ReplayWindows()
        windows.keep(link(7), 4)
        windows.keep(link(7), 11)
        assertEquals(11L, windows.resume(link(7)))
    }

    @Test
    fun theLeastRecentlySeenGoesFirstWhenFullAndTheRestStay() {
        val windows = ReplayWindows()
        for (i in 0 until 64) windows.keep(link(i), (i + 1).toLong())
        // Asking after the first one makes it the most recently seen, so the
        // second is what the sixty-fifth pushes out.
        assertEquals(1L, windows.resume(link(0)))
        windows.keep(link(200), 500)
        assertEquals(1L, windows.resume(link(0)))
        assertEquals(0L, windows.resume(link(1)))
        assertEquals(64L, windows.resume(link(63)))
        assertEquals(500L, windows.resume(link(200)))
    }
}
