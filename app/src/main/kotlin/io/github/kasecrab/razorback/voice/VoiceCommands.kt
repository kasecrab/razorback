package io.github.kasecrab.razorback.voice

/**
 * The few things the person can say that the app acts on itself, without the model.
 * A direct "close voice mode" ends the session at once; anything less plain goes to the
 * model, which has a tool for the same thing and can say goodbye first.
 */
object VoiceCommands {

    private val verbs = setOf("close", "exit", "end", "stop", "quit", "leave", "finish", "turn")
    private val things = listOf("voice mode", "voice chat", "voice conversation", "voice screen", "the screen", "this screen", "voice off", "voice")

    /** True for a short, plain request to close voice mode, such as "close the voice mode please". */
    fun closes(transcript: String): Boolean {
        val words = transcript.lowercase().replace(Regex("[^a-z ]"), " ").trim().split(Regex(" +")).filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > MAX_WORDS) return false
        if (words.none { it in verbs }) return false
        val text = words.joinToString(" ")
        return things.any { text.contains(it) }
    }

    private const val MAX_WORDS = 8
}
