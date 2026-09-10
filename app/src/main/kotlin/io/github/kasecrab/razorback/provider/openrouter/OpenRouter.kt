package io.github.kasecrab.razorback.provider.openrouter

object OpenRouter {
    const val ID = "openrouter"
    const val DEFAULT_BASE = "https://openrouter.ai/api/v1"

    /** Debug builds can point this at a local mock server. */
    @Volatile var BASE: String = DEFAULT_BASE
    const val KEYS_URL = "https://openrouter.ai/settings/keys"

    fun headers(key: String): Map<String, String> = mapOf(
        "Authorization" to "Bearer $key",
        "HTTP-Referer" to "https://github.com/kasecrab/razorback",
        "X-Title" to "Razorback",
    )
}
