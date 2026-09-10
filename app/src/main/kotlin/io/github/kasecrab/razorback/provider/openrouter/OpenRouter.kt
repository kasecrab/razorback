package io.github.kasecrab.razorback.provider.openrouter

object OpenRouter {
    const val ID = "openrouter"
    const val BASE = "https://openrouter.ai/api/v1"
    const val KEYS_URL = "https://openrouter.ai/settings/keys"

    fun headers(key: String): Map<String, String> = mapOf(
        "Authorization" to "Bearer $key",
        "HTTP-Referer" to "https://github.com/kasecrab/razorback",
        "X-Title" to "Razorback",
    )
}
