package io.github.kasecrab.razorback.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SseTest {

    private fun run(vararg lines: String): List<Any> {
        val sse = Sse()
        val out = ArrayList<Any>()
        for (l in lines) sse.push(l)?.let { out.add(it) }
        sse.flush()?.let { out.add(it) }
        return out
    }

    private fun texts(items: List<Any>) = items.map { if (it is Sse.Item.Data) it.text else "DONE" }

    @Test
    fun commentLinesAreDropped() {
        val items = run(": OPENROUTER PROCESSING", "", "data: {\"a\":1}", "", "data: [DONE]", "")
        assertEquals(listOf("{\"a\":1}", "DONE"), texts(items))
    }

    @Test
    fun crlfIsTolerated() {
        val items = run("data: x\r", "\r")
        assertEquals(listOf("x"), texts(items))
    }

    @Test
    fun multiLineDataJoinsWithNewline() {
        val items = run("data: a", "data: b", "")
        assertEquals(listOf("a\nb"), texts(items))
    }

    @Test
    fun otherFieldsAreIgnored() {
        val items = run("event: ping", "id: 3", "data: z", "")
        assertEquals(listOf("z"), texts(items))
    }

    @Test
    fun trailingDataWithoutBlankLineFlushesAtEof() {
        val items = run("data: tail")
        assertEquals(listOf("tail"), texts(items))
    }

    @Test
    fun emptyStreamYieldsNothing() {
        assertTrue(run("", "").isEmpty())
        assertNull(Sse().flush())
    }
}
