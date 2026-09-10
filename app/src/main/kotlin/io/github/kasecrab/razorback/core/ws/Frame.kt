package io.github.kasecrab.razorback.core.ws

import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.OutputStream
import java.security.SecureRandom

/** RFC 6455 framing, byte level only, so it can be tested without a socket. */
object Frame {

    const val CONTINUATION = 0x0
    const val TEXT = 0x1
    const val BINARY = 0x2
    const val CLOSE = 0x8
    const val PING = 0x9
    const val PONG = 0xA

    const val MAX_PAYLOAD = 16 * 1024 * 1024

    class Header(val fin: Boolean, val opcode: Int, val length: Long, val mask: ByteArray?)

    private val random = SecureRandom()

    /** Client frames are always masked with a fresh key. */
    fun write(out: OutputStream, opcode: Int, payload: ByteArray, offset: Int = 0, length: Int = payload.size - offset, fin: Boolean = true) {
        val header = ByteArray(14)
        var n = 0
        header[n++] = ((if (fin) 0x80 else 0) or opcode).toByte()
        when {
            length < 126 -> header[n++] = (0x80 or length).toByte()
            length < 65536 -> {
                header[n++] = (0x80 or 126).toByte()
                header[n++] = (length ushr 8).toByte()
                header[n++] = length.toByte()
            }
            else -> {
                header[n++] = (0x80 or 127).toByte()
                for (i in 7 downTo 0) header[n++] = (length.toLong() ushr (i * 8)).toByte()
            }
        }
        val key = ByteArray(4)
        random.nextBytes(key)
        System.arraycopy(key, 0, header, n, 4)
        n += 4
        out.write(header, 0, n)
        val masked = ByteArray(length)
        for (i in 0 until length) masked[i] = (payload[offset + i].toInt() xor key[i and 3].toInt()).toByte()
        out.write(masked)
    }

    @Throws(IOException::class)
    fun readHeader(input: DataInputStream): Header {
        val b0 = input.readUnsignedByte()
        val b1 = input.readUnsignedByte()
        val fin = b0 and 0x80 != 0
        if (b0 and 0x70 != 0) throw IOException("reserved bits set")
        val opcode = b0 and 0x0F
        val masked = b1 and 0x80 != 0
        var len = (b1 and 0x7F).toLong()
        if (len == 126L) {
            len = input.readUnsignedShort().toLong()
        } else if (len == 127L) {
            len = input.readLong()
        }
        if (len < 0 || len > MAX_PAYLOAD) throw IOException("frame too large: $len")
        if (opcode >= 0x8 && (len > 125 || !fin)) throw IOException("bad control frame")
        val mask = if (masked) ByteArray(4).also { input.readFully(it) } else null
        return Header(fin, opcode, len, mask)
    }

    @Throws(IOException::class)
    fun readPayload(input: DataInputStream, h: Header): ByteArray {
        val data = ByteArray(h.length.toInt())
        try {
            input.readFully(data)
        } catch (e: EOFException) {
            throw IOException("stream ended inside a frame")
        }
        val mask = h.mask
        if (mask != null) for (i in data.indices) data[i] = (data[i].toInt() xor mask[i and 3].toInt()).toByte()
        return data
    }

    /** Close payload: two-byte status then optional UTF-8 reason. */
    fun closePayload(code: Int, reason: String = ""): ByteArray {
        val r = reason.toByteArray(Charsets.UTF_8)
        val out = ByteArray(2 + r.size)
        out[0] = (code ushr 8).toByte()
        out[1] = code.toByte()
        System.arraycopy(r, 0, out, 2, r.size)
        return out
    }

    fun closeCode(payload: ByteArray): Int =
        if (payload.size >= 2) ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF) else 1005
}
