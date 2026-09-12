package io.github.kasecrab.razorback.remote

import java.net.URI

/**
 * What counts as a relay address. The socket underneath is a plain TCP socket with TLS
 * put on by hand, so the platform's cleartext rule does not cover it; the rule lives
 * here instead: https, or loopback for a relay run on the phone's own host for testing.
 */
object RelayUrl {

    fun clean(url: String): String = url.trim().trimEnd('/')

    /** True for an https address with a host, or plain http to loopback only. */
    fun acceptable(url: String): Boolean {
        val u = clean(url)
        if (u.any { it.isWhitespace() }) return false
        val parsed = try {
            URI(u)
        } catch (_: Exception) {
            return false
        }
        val host = parsed.host ?: return false
        return when (parsed.scheme) {
            "https" -> host.isNotEmpty()
            "http" -> host == "localhost" || host == "127.0.0.1" || host == "[::1]" || host == "::1"
            else -> false
        }
    }

    /** The host, for saying which relay something is about. */
    fun host(url: String): String = try {
        URI(clean(url)).host ?: clean(url)
    } catch (_: Exception) {
        clean(url)
    }

    /** The websocket form of an accepted address. */
    fun socket(url: String): String {
        val u = clean(url)
        return when {
            u.startsWith("https://") -> "wss://" + u.substring(8)
            u.startsWith("http://") -> "ws://" + u.substring(7)
            else -> u
        }
    }
}
