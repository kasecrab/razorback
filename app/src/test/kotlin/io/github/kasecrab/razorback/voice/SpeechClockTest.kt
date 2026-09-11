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

    @Test
    fun theHighlightNeverStepsBack() {
        val c = SpeechClock()
        // A guessed run: the highlight runs ahead on the guess, then the real length turns out longer.
        c.sentence("One two three four five six seven eight nine ten.")
        c.audio(60000)
        c.seek(60000)
        val ahead = c.word
        assertTrue(ahead >= 3)
        c.audio(200000)
        c.flushed()
        // The same played position now maps to an earlier word; the highlight waits instead.
        assertFalse(c.seek(60000))
        assertEquals(ahead, c.word)
        // Once the voice has really got there, it moves on.
        c.seek(250000)
        assertEquals(9, c.word)
    }

    @Test
    fun aRunStillArrivingIsGuessedAtThePaceHeardSoFar() {
        val c = SpeechClock()
        // Fifty characters took 300000 bytes: 6000 bytes a character, twice the default.
        c.sentence("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        c.audio(300000)
        c.flushed()
        c.sentence("One two three four five six seven eight nine ten.")
        c.audio(1000)
        // At 6000 bytes a character, 15000 bytes in is still "One"; at the default pace it would be "two".
        c.seek(300000 + 15000)
        assertEquals(1, c.word)
        c.reset()
        // The pace survives a reset, so the next reply's first run is guessed at it too.
        c.sentence("One two three four five six seven eight nine ten.")
        c.audio(1000)
        c.seek(15000)
        assertEquals(0, c.word)
    }

    @Test
    fun audioAheadOfItsRunBelongsToTheNextOne() {
        val c = SpeechClock()
        c.audio(5000)
        c.sentence("Hello there.")
        c.audio(1000)
        c.flushed()
        c.sentence("Second.")
        c.audio(6000)
        c.flushed()
        // Six thousand bytes in is still the first run; the next byte is the second.
        c.seek(5999)
        assertEquals(1, c.word)
        c.seek(6000)
        assertEquals(2, c.word)
    }
}
