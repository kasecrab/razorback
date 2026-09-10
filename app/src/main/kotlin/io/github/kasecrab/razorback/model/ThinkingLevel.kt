package io.github.kasecrab.razorback.model

/** Reasoning effort sent to the provider; OFF omits the field entirely. */
enum class ThinkingLevel(val label: String, val hint: String) {
    OFF("Off", "No reasoning"),
    MINIMAL("Minimal", "A few tokens of thought"),
    LOW("Low", "Quick"),
    MEDIUM("Medium", "Balanced"),
    HIGH("High", "Thorough"),
    XHIGH("Extra high", "Maximum, slow"),
    MAX("Max", "Everything the model has"),
    ;

    val wire: String get() = name.lowercase()

    companion object {
        fun fromName(name: String?): ThinkingLevel = entries.firstOrNull { it.name == name } ?: OFF
    }
}
