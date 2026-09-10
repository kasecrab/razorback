package io.github.kasecrab.razorback.provider.openrouter

import io.github.kasecrab.razorback.core.arr
import io.github.kasecrab.razorback.core.dbl
import io.github.kasecrab.razorback.core.forEachObject
import io.github.kasecrab.razorback.core.int
import io.github.kasecrab.razorback.core.jsonObject
import io.github.kasecrab.razorback.core.obj
import io.github.kasecrab.razorback.core.str
import io.github.kasecrab.razorback.model.Message
import io.github.kasecrab.razorback.model.Role
import io.github.kasecrab.razorback.model.ThinkingLevel
import io.github.kasecrab.razorback.model.Usage
import io.github.kasecrab.razorback.provider.ChatEvent
import io.github.kasecrab.razorback.provider.ChatRequest
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** Wire mapping for OpenRouter's OpenAI-shaped chat completions. Pure functions, unit-tested. */
object OpenRouterJson {

    fun toWire(r: ChatRequest): JSONObject = jsonObject {
        put("model", r.model)
        put("stream", true)
        put("usage", jsonObject { put("include", true) })
        val msgs = JSONArray()
        val sys = r.systemPrompt?.trim()
        if (!sys.isNullOrEmpty()) msgs.put(jsonObject { put("role", "system"); put("content", sys) })
        for (m in r.messages) msgs.put(message(m))
        put("messages", msgs)
        r.maxTokens?.let { put("max_tokens", it) }
        r.temperature?.let { put("temperature", it.toDouble()) }
        if (r.thinking != ThinkingLevel.OFF) put("reasoning", jsonObject { put("effort", r.thinking.wire) })
        if (r.tools.isNotEmpty()) put("tools", JSONArray(r.tools))
        if (r.imageOutput) put("modalities", JSONArray().put("image").put("text"))
    }

    private fun message(m: Message): JSONObject = jsonObject {
        put("role", m.role.wire)
        when (m.role) {
            Role.USER -> {
                if (m.images.isEmpty()) {
                    put("content", m.content)
                } else {
                    val parts = JSONArray()
                    if (m.content.isNotEmpty()) parts.put(jsonObject { put("type", "text"); put("text", m.content) })
                    for (url in m.images) parts.put(imagePart(url))
                    put("content", parts)
                }
            }
            Role.ASSISTANT -> {
                put("content", m.content)
                m.reasoningDetails?.let { put("reasoning_details", JSONArray(it)) }
                if (m.toolCalls.isNotEmpty()) {
                    val calls = JSONArray()
                    for (c in m.toolCalls) {
                        calls.put(jsonObject {
                            put("id", c.id)
                            put("type", "function")
                            put("function", jsonObject { put("name", c.name); put("arguments", c.arguments) })
                        })
                    }
                    put("tool_calls", calls)
                }
                if (m.images.isNotEmpty()) {
                    val imgs = JSONArray()
                    for (url in m.images) imgs.put(imagePart(url))
                    put("images", imgs)
                }
            }
            Role.TOOL -> {
                put("tool_call_id", m.toolCallId)
                put("content", m.content)
            }
            Role.SYSTEM -> put("content", m.content)
        }
    }

    private fun imagePart(url: String) = jsonObject {
        put("type", "image_url")
        put("image_url", jsonObject { put("url", url) })
    }

    /** One SSE data payload to zero or more events. Unknown shapes are ignored, not fatal. */
    fun parseChunk(chunk: JSONObject, out: MutableList<ChatEvent>) {
        chunk.obj("error")?.let {
            out.add(ChatEvent.Failure(200, errorText(it)))
            return
        }
        val choices = chunk.arr("choices")
        if (choices != null && choices.length() > 0) {
            val choice = choices.optJSONObject(0)
            if (choice != null) {
                choice.obj("error")?.let {
                    out.add(ChatEvent.Failure(200, errorText(it)))
                    return
                }
                val delta = choice.obj("delta")
                if (delta != null) {
                    delta.str("reasoning")?.takeIf { it.isNotEmpty() }?.let { out.add(ChatEvent.Reasoning(it)) }
                    delta.arr("reasoning_details")?.forEachObject { out.add(ChatEvent.ReasoningDetail(it.toString())) }
                    delta.str("content")?.takeIf { it.isNotEmpty() }?.let { out.add(ChatEvent.Text(it)) }
                    images(delta.arr("images"), out)
                    delta.arr("tool_calls")?.forEachObject { tc ->
                        val fn = tc.obj("function")
                        out.add(
                            ChatEvent.ToolCallDelta(
                                index = tc.int("index") ?: 0,
                                id = tc.str("id"),
                                name = fn?.str("name"),
                                arguments = fn?.str("arguments") ?: "",
                            ),
                        )
                    }
                }
                images(choice.obj("message")?.arr("images"), out)
                choice.str("finish_reason")?.let { out.add(ChatEvent.Finish(it)) }
            }
        }
        chunk.obj("usage")?.let { u ->
            out.add(
                ChatEvent.UsageReport(
                    Usage(
                        promptTokens = u.int("prompt_tokens") ?: 0,
                        completionTokens = u.int("completion_tokens") ?: 0,
                        reasoningTokens = u.obj("completion_tokens_details")?.int("reasoning_tokens") ?: 0,
                        cachedTokens = u.obj("prompt_tokens_details")?.int("cached_tokens") ?: 0,
                        cost = u.dbl("cost") ?: 0.0,
                        cacheDiscount = abs(u.dbl("cache_discount") ?: 0.0),
                    ),
                ),
            )
        }
    }

    private fun images(arr: JSONArray?, out: MutableList<ChatEvent>) {
        arr?.forEachObject { img ->
            val url = when (val v = img.opt("image_url")) {
                is JSONObject -> v.str("url")
                is String -> v
                else -> img.str("url")
            }
            if (url != null && url.startsWith("data:")) out.add(ChatEvent.Image(url))
        }
    }

    private fun errorText(err: JSONObject): String {
        val msg = err.str("message") ?: err.toString()
        val code = err.opt("code")
        return if (code != null && code != JSONObject.NULL) "$msg ($code)" else msg
    }
}
