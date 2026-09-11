package io.github.kasecrab.razorback.voice

/** Reads RIFF/WAVE files holding 16-bit PCM, whatever their rate or channel count. */
object Wav {

    /** [length] bytes of interleaved PCM16 at [offset] into [bytes]. */
    class Clip(val bytes: ByteArray, val offset: Int, val length: Int, val rate: Int, val channels: Int) {
        val frames: Int get() = length / (2 * channels)
    }

    /**
     * Sizes in the header are not trusted: streamed recordings carry placeholders, and a
     * file fetched by range is cut short. Null for anything that is not plain PCM16.
     */
    fun parse(b: ByteArray): Clip? {
        if (b.size < 12 || ascii(b, 0) != "RIFF" || ascii(b, 8) != "WAVE") return null
        var i = 12
        var rate = 0
        var channels = 0
        var bits = 0
        var format = 0
        while (i + 8 <= b.size) {
            val id = ascii(b, i)
            val size = le32(b, i + 4)
            val body = i + 8
            if (id == "fmt " && body + 16 <= b.size) {
                format = le16(b, body)
                channels = le16(b, body + 2)
                rate = le32(b, body + 4)
                bits = le16(b, body + 14)
            } else if (id == "data") {
                if (format != 1 || bits != 16 || channels !in 1..2 || rate < 8000) return null
                val available = b.size - body
                val length = minOf(if (size < 0) available else size, available) and (2 * channels - 1).inv()
                return if (length > 0) Clip(b, body, length, rate, channels) else null
            }
            if (size < 0 || body + size < body) return null
            i = body + size + (size and 1)
        }
        return null
    }

    private fun ascii(b: ByteArray, at: Int) = String(b, at, 4, Charsets.US_ASCII)
    private fun le16(b: ByteArray, at: Int) = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)
    private fun le32(b: ByteArray, at: Int) = le16(b, at) or (le16(b, at + 2) shl 16)
}
