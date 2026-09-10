package io.github.kasecrab.razorback.model

class ModelInfo(
    val id: String,
    val name: String,
    val contextLength: Int,
    /** USD per million tokens. */
    val promptPerM: Double,
    val completionPerM: Double,
    val imageOutPerM: Double,
    val supportsReasoning: Boolean,
    val supportsTools: Boolean,
    val inputModalities: Set<String>,
    val outputModalities: Set<String>,
    /** What the router says about the model's thinking; null when it says nothing. */
    val reasoning: ReasoningInfo? = null,
) {
    val acceptsImages: Boolean get() = "image" in inputModalities
    val acceptsFiles: Boolean get() = "file" in inputModalities
    val producesImages: Boolean get() = "image" in outputModalities
    val producesText: Boolean get() = "text" in outputModalities
    val isFree: Boolean get() = promptPerM == 0.0 && completionPerM == 0.0

    /** Last path segment reads well when the display name is missing or long. */
    val shortName: String get() = id.substringAfter('/').substringBefore(':')
}

/**
 * How a model reasons, from the router's model list: whether it can be told not to,
 * whether it does unless told otherwise, and which effort words it accepts.
 */
class ReasoningInfo(
    val mandatory: Boolean,
    val defaultEnabled: Boolean,
    /** Router effort words, e.g. ["max","high","low"]; null when any is fine. */
    val supportedEfforts: List<String>?,
    val defaultEffort: String?,
)
