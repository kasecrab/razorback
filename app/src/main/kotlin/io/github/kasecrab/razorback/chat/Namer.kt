package io.github.kasecrab.razorback.chat

import io.github.kasecrab.razorback.core.Http
import io.github.kasecrab.razorback.core.Log
import io.github.kasecrab.razorback.core.arr
import io.github.kasecrab.razorback.core.jsonArrayOf
import io.github.kasecrab.razorback.core.jsonObject
import io.github.kasecrab.razorback.core.obj
import io.github.kasecrab.razorback.core.str
import io.github.kasecrab.razorback.provider.openrouter.OpenRouter

/**
 * Gives a new chat its name: a small fast model reads what the person first said and
 * answers with a few words. Blocking; call it off the main thread.
 */
class Namer(private val key: () -> String?) {

    /** A short title for a chat that opened with [firstMessage], or null when none could be had. */
    fun name(firstMessage: String): String? {
        val apiKey = key() ?: return null
        val body = jsonObject {
            put("model", MODEL)
            // The model thinks before it answers and the thinking counts; a tight cap cuts the title off.
            put("max_tokens", 200)
            put("temperature", 0.3)
            put("reasoning", jsonObject { put("effort", "low") })
            put(
                "messages",
                jsonArrayOf(
                    jsonObject {
                        put("role", "system")
                        put("content", PROMPT)
                    },
                    jsonObject {
                        put("role", "user")
                        put("content", firstMessage.take(MAX_INPUT))
                    },
                ),
            )
        }
        val reply = try {
            Http.postJson("${OpenRouter.BASE}/chat/completions", OpenRouter.headers(apiKey), body)
        } catch (e: Exception) {
            Log.w("chat naming failed: ${e.message}")
            return null
        }
        val choice = reply.arr("choices")?.optJSONObject(0) ?: return null
        if (choice.str("finish_reason") == "length") return null
        return clean(choice.obj("message")?.str("content") ?: return null)
    }

    companion object {
        const val MODEL = "openai/gpt-oss-120b"
        const val MAX_INPUT = 800
        const val MAX_TITLE = 48
        const val PROMPT = "Name the chat that starts with the user's message. Reply with the title only: two to five words, " +
            "title case, no quotes, no trailing period, no emoji, in the user's language."

        /** The first line, without the wrapping a model may add, and never longer than a drawer row. */
        fun clean(raw: String): String? {
            var t = raw.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
            t = t.trim('"', '\'', '`', '*', '#', ' ', '“', '”', '‘', '’')
            t = t.removePrefix("Title:").removePrefix("title:").trim().trimEnd('.', '。').trim()
            t = t.replace(Regex("\\s+"), " ")
            if (t.isEmpty()) return null
            return if (t.length > MAX_TITLE) t.take(MAX_TITLE - 1).trimEnd() + "…" else t
        }
    }
}
