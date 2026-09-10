package io.github.kasecrab.razorback.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SentenceChunkerTest {

    private fun run(text: String, chunk: Int = 3): List<String> {
        val out = ArrayList<String>()
        val c = SentenceChunker { out.add(it) }
        var i = 0
        while (i < text.length) {
            val e = minOf(text.length, i + chunk)
            c.push(text.substring(i, e))
            i = e
        }
        c.flush()
        return out
    }

    @Test
    fun splitsOnSentenceEnds() {
        assertEquals(listOf("The sky is blue today.", "Water boils at one hundred degrees!", "Really?"), run("The sky is blue today. Water boils at one hundred degrees! Really?"))
    }

    @Test
    fun keepsDecimalsAndAbbreviations() {
        assertEquals(listOf("Pi is about 3.14 and e.g. Dr. Who agrees with Mr. Smith.", "Fine then."), run("Pi is about 3.14 and e.g. Dr. Who agrees with Mr. Smith. Fine then."))
    }

    @Test
    fun shortFragmentsMergeWithTheNext() {
        assertEquals(listOf("Ok. Then we should go outside now.", "Yes."), run("Ok. Then we should go outside now. Yes."))
    }

    @Test
    fun newlinesEndSentencesAndFencesStayWhole() {
        val r = run("First line of the answer\nSecond line here.\n```\nx = 1. y = 2.\n```\nAfter the code block.")
        assertEquals("First line of the answer", r[0])
        assertEquals("Second line here.", r[1])
        assertTrue(r.any { it.contains("x = 1. y = 2.") })
        assertEquals("After the code block.", r.last())
    }

    @Test
    fun longRunsAreCutAtCommas() {
        val long = (1..40).joinToString(", ") { "item $it" } + "."
        val r = run(long)
        assertTrue(r.size >= 2)
        assertTrue(r.all { it.length <= 260 })
        assertEquals(long.replace(" ", ""), r.joinToString("").replace(" ", ""))
    }

    @Test
    fun chunkSizeDoesNotChangeTheResult() {
        val text = "Hello there. How are you doing today? I am fine, thanks! See you at 5.30 p.m. tomorrow."
        val expected = run(text, 1000)
        val rnd = Random(7)
        repeat(20) {
            val out = ArrayList<String>()
            val c = SentenceChunker { out.add(it) }
            var i = 0
            while (i < text.length) {
                val e = minOf(text.length, i + 1 + rnd.nextInt(6))
                c.push(text.substring(i, e))
                i = e
            }
            c.flush()
            assertEquals(expected, out)
        }
    }

    @Test
    fun unicodeSentenceEnds() {
        assertEquals(listOf("今日はいい天気ですね。", "散歩に行きましょう！"), run("今日はいい天気ですね。散歩に行きましょう！"))
    }
}

class SpeechTextTest {
    @Test
    fun stripsMarkdownForSpeech() {
        val md = "# Title\n\nSome **bold** and *it* text with `code` and a [link](https://x.y).\n\n- one\n- two\n\n```kt\nval x = 1\n```\n\n| a | b |\n|---|---|\n| 1 | 2 |\n\n> quoted"
        val s = SpeechText.strip(md)
        assertEquals("Title\n\nSome bold and it text with code and a link.\n\none\ntwo\n\nCode omitted.\n\nquoted", s)
    }
}
