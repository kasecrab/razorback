package io.github.kasecrab.razorback.model

/**
 * How hard the model should think. OFF is the router's "none"; the rest are its effort
 * words in ascending order, so [ordinal] doubles as a rank.
 */
enum class ThinkingLevel(val label: String, val hint: String) {
    OFF("Off", "No reasoning"),
    MINIMAL("Minimal", "A few tokens of thought"),
    LOW("Low", "Quick"),
    MEDIUM("Medium", "Balanced"),
    HIGH("High", "Thorough"),
    XHIGH("Extra high", "Maximum, slow"),
    MAX("Max", "Everything the model has"),
    ;

    val wire: String get() = if (this == OFF) "none" else name.lowercase()

    companion object {
        fun fromName(name: String?): ThinkingLevel = entries.firstOrNull { it.name == name } ?: OFF

        fun fromWire(word: String?): ThinkingLevel? = when (word) {
            "none" -> OFF
            null -> null
            else -> entries.firstOrNull { it.wire == word }
        }
    }
}
