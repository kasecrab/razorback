package io.github.kasecrab.razorback.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechClockTest {

    private fun clock(): SpeechClock {
        val c = SpeechClock()
        c.sentence("Hello world.")
        c.audio(48000)
        c.flushed()
        c.sentence("Second one here.")
        c.audio(30000)
        c.audio(42000)
        c.flushed()
        return c
    }

    @Test
    fun wordsAreSharedOutByLengthWithinEachSentence() {
        val c = clock()
        assertEquals(5, c.wordTotal)
        assertTrue(c.allFlushed)
        c.seek(0)
        assertEquals(0, c.word)
        assertEquals(0f, c.fraction, 1e-6f)
        // "Hello" 5.35 vs "world." 5+0.35+1.2: the first word ends near 45 % of the sentence.
        c.seek(20000)
        assertEquals(0, c.word)
        c.seek(30000)
        assertEquals(1, c.word)
        c.seek(47999)
        assertEquals(1, c.word)
        assertTrue(c.fraction > 0.9f)
        c.seek(48000)
        assertEquals(2, c.word)
        assertEquals(0f, c.fraction, 1e-6f)
        c.seek(48000 + 72000 - 1)
        assertEquals(4, c.word)
        c.seek(48000 + 72000 + 5000)
        assertEquals(4, c.word)
        assertEquals(1f, c.fraction, 1e-6f)
    }

    @Test
    fun aSentenceStillArrivingIsEstimatedFromItsLength() {
        val c = SpeechClock()
        c.sentence("One two three four five six seven eight nine ten.")
        c.audio(12000)
        assertFalse(c.allFlushed)
        c.seek(0)
        assertEquals(0, c.word)
        // 49 chars * 3000 bytes is the guess; a quarter of the way lands in the first few words.
        c.seek(49 * 3000L / 4)
        assertTrue(c.word in 1..3)
    }

    @Test
    fun seekReportsWhetherAnythingMoved() {
        val c = clock()
        assertTrue(c.seek(1000))
        assertFalse(c.seek(1000))
        assertTrue(c.seek(2000))
    }

    @Test
    fun resetForgetsEverything() {
        val c = clock()
        c.reset()
        assertEquals(0, c.sentenceCount)
        assertEquals(0, c.wordTotal)
        assertTrue(c.allFlushed)
        c.seek(100)
        assertEquals(-1, c.word)
    }

    @Test
    fun audioBeforeAnySentenceIsCountedButNotAssigned() {
        val c = SpeechClock()
        c.audio(100)
        assertEquals(100L, c.totalBytes)
        c.sentence("Hi.")
        c.audio(100)
        c.flushed()
        assertTrue(c.allFlushed)
    }
}
