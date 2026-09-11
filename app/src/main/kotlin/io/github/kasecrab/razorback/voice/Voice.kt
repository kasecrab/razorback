package io.github.kasecrab.razorback.voice

/** One text-to-speech voice as Deepgram describes it in its model catalogue. */
class Voice(
    /** The model name sent to the API, e.g. "aura-2-thalia-en". */
    val id: String,
    val name: String,
    /** Null when the catalogue does not say. */
    val feminine: Boolean?,
    val accent: String,
    val age: String,
    /** Deepgram's descriptors with the gender words taken out, lowercase. */
    val tags: List<String>,
    /** Base language code, e.g. "en". */
    val language: String,
    /** Deepgram's colour for the voice, 0 when unknown. */
    val color: Int,
    val architecture: String,
) {
    val traits: String get() = tags.joinToString(", ").replaceFirstChar { it.uppercase() }

    companion object {
        /** A stand-in for an id the catalogue has not been fetched for yet. */
        fun placeholder(id: String): Voice = Voice(
            id, id.removePrefix("aura-2-").removePrefix("aura-").substringBefore('-').replaceFirstChar { it.uppercase() },
            null, "", "", emptyList(), id.substringAfterLast('-'), 0, if (id.startsWith("aura-2-")) "aura-2" else "aura",
        )
    }
}
