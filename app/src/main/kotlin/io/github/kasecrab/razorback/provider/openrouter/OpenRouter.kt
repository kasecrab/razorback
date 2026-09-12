package io.github.kasecrab.razorback.provider.openrouter

object OpenRouter {
    const val ID = "openrouter"
    const val DEFAULT_BASE = "https://openrouter.ai/api/v1"

    /** Debug builds can point this at a local mock server; [mockServer] says which count. */
    @Volatile var BASE: String = DEFAULT_BASE

    /**
     * Whether a debug build will point itself at [url].
     *
     * The address arrives on an intent extra, which any app on the phone can send, and
     * every request afterwards carries the OpenRouter key in a header. So it is held to
     * the three hosts the debug network config lets this build speak to in the clear: this
     * phone, and the host the emulator runs on. Anywhere else on the internet would not be
     * cleartext and would have the key just the same.
     */
    fun mockServer(url: String): Boolean {
        val host = try {
            java.net.URI(url).host
        } catch (_: Exception) {
            null
        } ?: return false
        return host == "10.0.2.2" || host == "127.0.0.1" || host == "localhost" || host == "[::1]" || host == "::1"
    }

    fun headers(key: String): Map<String, String> = mapOf(
        "Authorization" to "Bearer $key",
        "HTTP-Referer" to "https://github.com/kasecrab/razorback",
        "X-Title" to "Razorback",
    )
}
