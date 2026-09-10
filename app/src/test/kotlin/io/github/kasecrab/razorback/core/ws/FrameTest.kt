package io.github.kasecrab.razorback.core.ws

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException

class FrameTest {

    private fun roundTrip(size: Int, opcode: Int = Frame.BINARY): Pair<Frame.Header, ByteArray> {
        val payload = ByteArray(size) { (it * 31).toByte() }
        val out = ByteArrayOutputStream()
        Frame.write(out, opcode, payload)
        val bytes = out.toByteArray()
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val h = Frame.readHeader(input)
        val data = Frame.readPayload(input, h)
        assertArrayEquals(payload, data)
        return h to bytes
    }

    @Test
    fun shortMediumAndLongLengths() {
        val (h1, b1) = roundTrip(125)
        assertEquals(125L, h1.length)
        assertEquals(2 + 4 + 125, b1.size)
        val (h2, b2) = roundTrip(126)
        assertEquals(126L, h2.length)
        assertEquals(4 + 4 + 126, b2.size)
        val (h3, b3) = roundTrip(65536)
        assertEquals(65536L, h3.length)
        assertEquals(10 + 4 + 65536, b3.size)
    }

    @Test
    fun clientFramesAreMaskedAndFinal() {
        val (h, bytes) = roundTrip(10, Frame.TEXT)
        assertTrue(h.fin)
        assertEquals(Frame.TEXT, h.opcode)
        assertNotNull(h.mask)
        assertEquals(0x80, bytes[1].toInt() and 0x80)
    }

    @Test
    fun unmaskedServerFrameDecodes() {
        val text = "hello".toByteArray()
        val bytes = byteArrayOf(0x81.toByte(), text.size.toByte()) + text
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val h = Frame.readHeader(input)
        assertEquals("hello", String(Frame.readPayload(input, h)))
    }

    @Test
    fun oversizedFrameIsRejected() {
        val header = byteArrayOf(0x82.toByte(), 127, 0, 0, 0, 0, 0x7F, 0, 0, 0)
        try {
            Frame.readHeader(DataInputStream(ByteArrayInputStream(header)))
            throw AssertionError("expected rejection")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("too large"))
        }
    }

    @Test
    fun closePayloadCarriesCodeAndReason() {
        val p = Frame.closePayload(1001, "bye")
        assertEquals(1001, Frame.closeCode(p))
        assertEquals("bye", String(p, 2, p.size - 2))
        assertEquals(1005, Frame.closeCode(ByteArray(0)))
    }
}
