package io.github.kasecrab.razorback.model

class Usage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val reasoningTokens: Int = 0,
    val cachedTokens: Int = 0,
    val cost: Double = 0.0,
    val cacheDiscount: Double = 0.0,
) {
    val totalTokens: Int get() = promptTokens + completionTokens

    operator fun plus(o: Usage) = Usage(
        promptTokens + o.promptTokens,
        completionTokens + o.completionTokens,
        reasoningTokens + o.reasoningTokens,
        cachedTokens + o.cachedTokens,
        cost + o.cost,
        cacheDiscount + o.cacheDiscount,
    )
}
