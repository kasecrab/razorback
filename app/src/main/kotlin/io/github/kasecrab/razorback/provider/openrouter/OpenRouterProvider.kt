package io.github.kasecrab.razorback.provider.openrouter

import io.github.kasecrab.razorback.core.Http
import io.github.kasecrab.razorback.core.Log
import io.github.kasecrab.razorback.core.Sse
import io.github.kasecrab.razorback.core.stream
import io.github.kasecrab.razorback.model.ModelInfo
import io.github.kasecrab.razorback.provider.ChatEvent
import io.github.kasecrab.razorback.provider.ChatRequest
import io.github.kasecrab.razorback.provider.Provider
import io.github.kasecrab.razorback.provider.StreamHandle
import org.json.JSONException
import org.json.JSONObject

class OpenRouterProvider(private val key: () -> String?) : Provider {

    override val id: String = OpenRouter.ID

    override fun streamChat(request: ChatRequest, handle: StreamHandle, onEvent: (ChatEvent) -> Unit) {
        val apiKey = key() ?: throw IllegalStateException("no OpenRouter key")
        val sse = Sse()
        val events = ArrayList<ChatEvent>(8)
        var open = true
        Http.stream(
            url = "${OpenRouter.BASE}/chat/completions",
            headers = OpenRouter.headers(apiKey),
            body = OpenRouterJson.toWire(request).toString(),
            handle = handle,
            onHeaders = { onEvent(ChatEvent.Started(it.getHeaderField("X-Generation-Id"))) },
        ) { line ->
            when (val item = sse.push(line)) {
                is Sse.Item.Data -> {
                    val chunk = try {
                        JSONObject(item.text)
                    } catch (e: JSONException) {
                        Log.w("unparseable stream chunk")
                        null
                    }
                    if (chunk != null) {
                        events.clear()
                        OpenRouterJson.parseChunk(chunk, events)
                        for (e in events) {
                            onEvent(e)
                            if (e is ChatEvent.Failure) open = false
                        }
                    }
                }
                Sse.Item.Done -> open = false
                null -> {}
            }
            open && !handle.cancelled
        }
    }

    override fun listModels(): List<ModelInfo> = OpenRouterModels.fetch(key())
}
