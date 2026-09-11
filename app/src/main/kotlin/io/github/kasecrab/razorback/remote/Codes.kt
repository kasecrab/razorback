package io.github.kasecrab.razorback.remote

/**
 * The pairing code as something a person can read off a screen and type.
 *
 * Base32 rather than base64: no case to get wrong, and the alphabet has no
 * `0` or `1` in it, so a zero typed where an `O` was shown can be put back
 * without guessing.
 */
object Codes {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val BYTES = 20

    /** The code as it was typed, or null if it is not one. */
    fun parse(typed: String): ByteArray? {
        val clean = StringBuilder(32)
        for (c in typed) {
            when {
                c == '-' || c == ' ' || c == '\t' || c == '–' || c == '—' -> continue
                // Neither is in base32, so neither can be what was meant, and
                // each has exactly one letter it could have been read from.
                c == '0' -> clean.append('O')
                c == '1' -> clean.append('I')
                c.isLetterOrDigit() -> clean.append(c.uppercaseChar())
                else -> return null
            }
        }
        if (clean.length != 32) return null
        val out = ByteArray(BYTES)
        var acc = 0L
        var bits = 0
        var at = 0
        for (c in clean) {
            val v = ALPHABET.indexOf(c)
            if (v < 0) return null
            acc = (acc shl 5) or v.toLong()
            bits += 5
            if (bits >= 8) {
                bits -= 8
                if (at >= BYTES) return null
                out[at++] = ((acc shr bits) and 0xFF).toByte()
            }
        }
        return if (at == BYTES) out else null
    }

    /** The code as it is shown: 32 characters in eight groups. */
    fun format(code: ByteArray): String {
        val raw = StringBuilder(32)
        var acc = 0L
        var bits = 0
        for (b in code) {
            acc = (acc shl 8) or (b.toLong() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                raw.append(ALPHABET[((acc shr bits) and 0x1F).toInt()])
            }
        }
        if (bits > 0) raw.append(ALPHABET[((acc shl (5 - bits)) and 0x1F).toInt()])
        return raw.chunked(4).joinToString("-")
    }
}
