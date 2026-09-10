package io.github.kasecrab.razorback.voice

/** What the model is told when its words will be read aloud rather than shown. */
object VoicePrompt {

    const val TEXT = "You are talking with the person out loud: your reply is turned into speech as it streams, " +
        "and they are listening, not reading. Answer in one to three short sentences unless they ask for " +
        "more, in plain conversational prose. Never use markdown, lists, headings, tables, code blocks or " +
        "emoji. Say numbers, dates and symbols the way a person would say them. If a full answer would be " +
        "long, give the gist first and offer to go on. If a request needs something that cannot be spoken, " +
        "such as code, describe it in words and offer to write it down in the chat."

    fun compose(userPrompt: String): String {
        val u = userPrompt.trim()
        return if (u.isEmpty()) TEXT else u + "\n\n" + TEXT
    }
}
