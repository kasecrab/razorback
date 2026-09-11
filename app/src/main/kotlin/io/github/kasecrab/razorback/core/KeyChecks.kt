package io.github.kasecrab.razorback.core

import java.security.MessageDigest

/**
 * Remembers that a vendor accepted a key, so a key that already works is not asked about
 * again on every visit. Only a fingerprint of the key is written, never the key itself, and
 * a fingerprint that no longer matches counts as unverified: editing a key drops its tick
 * instead of vouching for a key nobody has checked.
 */
class KeyChecks(private val prefs: Prefs) {

    /** When [key] was last accepted for [name], or null if this exact key never was. */
    fun verifiedAt(name: String, key: String): Long? {
        if (key.isEmpty()) return null
        val parts = prefs.rawString(PREFIX + name)?.split(SEP) ?: return null
        if (parts.size != 2 || parts[0] != fingerprint(key)) return null
        return parts[1].toLongOrNull()
    }

    fun accepted(name: String, key: String) {
        prefs.putRawString(PREFIX + name, fingerprint(key) + SEP + System.currentTimeMillis())
    }

    fun forget(name: String) = prefs.putRawString(PREFIX + name, null)

    /** The first eight bytes of SHA-256: enough to tell two keys apart, not enough to read one back. */
    private fun fingerprint(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        val out = StringBuilder(16)
        for (i in 0 until 8) {
            out.append(HEX[(digest[i].toInt() shr 4) and 0xF])
            out.append(HEX[digest[i].toInt() and 0xF])
        }
        return out.toString()
    }

    private companion object {
        const val PREFIX = "verified."
        const val SEP = "|"
        const val HEX = "0123456789abcdef"
    }
}
