package io.github.kasecrab.razorback.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpokenTest {

    @Test
    fun interimReplacesAndFinalAppends() {
        val s = Spoken()
        s.interim("fix the")
        assertEquals("fix the", s.text)
        s.interim("fix the auth")
        assertEquals("fix the auth", s.text)
        s.final("fix the auth middleware")
        assertEquals("fix the auth middleware", s.text)
        s.interim("in app")
        assertEquals("fix the auth middleware in app", s.text)
        s.final(", please.")
        assertEquals("fix the auth middleware, please.", s.text)
    }

    @Test
    fun stragglerAfterReleaseStillCounts() {
        val s = Spoken()
        s.release(1000)
        assertFalse(s.settled(1100, listening = false))
        s.final("late words")
        assertTrue(s.settled(1200, listening = false))
        assertEquals("late words", s.text)
    }

    @Test
    fun settleWindowExpires() {
        val s = Spoken()
        s.interim("half")
        s.release(1000)
        assertFalse(s.settled(1500, listening = false))
        assertTrue(s.settled(1000 + Spoken.SETTLE_MS + 1, listening = false))
        assertFalse(s.settled(5000, listening = true))
    }
}
