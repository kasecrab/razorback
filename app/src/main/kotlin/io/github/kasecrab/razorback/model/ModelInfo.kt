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
) {
    val acceptsImages: Boolean get() = "image" in inputModalities
    val acceptsFiles: Boolean get() = "file" in inputModalities
    val producesImages: Boolean get() = "image" in outputModalities
    val producesText: Boolean get() = "text" in outputModalities
    val isFree: Boolean get() = promptPerM == 0.0 && completionPerM == 0.0

    /** Last path segment reads well when the display name is missing or long. */
    val shortName: String get() = id.substringAfter('/').substringBefore(':')
}
