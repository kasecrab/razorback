package io.github.kasecrab.razorback.provider.openrouter

import io.github.kasecrab.razorback.model.Message
import io.github.kasecrab.razorback.model.ModelInfo
import io.github.kasecrab.razorback.model.ReasoningInfo
import io.github.kasecrab.razorback.model.Role
import io.github.kasecrab.razorback.model.ThinkingLevel
import io.github.kasecrab.razorback.provider.Accumulator
import io.github.kasecrab.razorback.provider.ChatEvent
import io.github.kasecrab.razorback.provider.ChatRequest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterJsonTest {

    private fun events(json: String): List<ChatEvent> {
        val out = ArrayList<ChatEvent>()
        OpenRouterJson.parseChunk(JSONObject(json), out)
        return out
    }

    @Test
    fun textAndReasoningDeltas() {
        val e = events("""{"choices":[{"delta":{"reasoning":"hm","content":"Hi"}}]}""")
        assertTrue(e[0] is ChatEvent.Reasoning)
        assertEquals("Hi", (e[1] as ChatEvent.Text).text)
    }

    @Test
    fun toolCallDeltasAccumulateByIndex() {
        val acc = Accumulator()
        for (chunk in listOf(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_a","function":{"name":"web_search","arguments":""}}]}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"q\":"}}]}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"x\"}"}}]}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":1,"function":{"name":"other"}}]}}]}""",
            """{"choices":[{"delta":{},"finish_reason":"tool_calls"}]}""",
        )) {
            for (e in events(chunk)) acc.apply(e)
        }
        val calls = acc.toolCalls()
        assertEquals(2, calls.size)
        assertEquals("call_a", calls[0].id)
        assertEquals("web_search", calls[0].name)
        assertEquals("""{"q":"x"}""", calls[0].arguments)
        assertEquals("call_1", calls[1].id)
        assertEquals("{}", calls[1].arguments)
        assertEquals("tool_calls", acc.finishReason)
    }

    @Test
    fun usageChunkWithoutChoices() {
        val e = events("""{"choices":[],"usage":{"prompt_tokens":10,"completion_tokens":5,"cost":0.0012,"cache_discount":-0.0002,"prompt_tokens_details":{"cached_tokens":4},"completion_tokens_details":{"reasoning_tokens":3}}}""")
        val u = (e.single() as ChatEvent.UsageReport).usage
        assertEquals(10, u.promptTokens)
        assertEquals(5, u.completionTokens)
        assertEquals(3, u.reasoningTokens)
        assertEquals(4, u.cachedTokens)
        assertEquals(0.0012, u.cost, 1e-9)
        assertEquals(0.0002, u.cacheDiscount, 1e-9)
    }

    @Test
    fun midStreamErrorBecomesFailure() {
        val e = events("""{"error":{"message":"rate limited","code":429},"choices":[{"finish_reason":"error"}]}""")
        val f = e.single() as ChatEvent.Failure
        assertEquals("rate limited (429)", f.message)
    }

    @Test
    fun imagesAcceptObjectOrBareStringAndDropRemoteUrls() {
        val e = events("""{"choices":[{"delta":{"images":[{"image_url":{"url":"data:image/png;base64,AA"}},{"image_url":"data:image/png;base64,BB"},{"image_url":{"url":"https://x/y.png"}}]}}]}""")
        assertEquals(listOf("data:image/png;base64,AA", "data:image/png;base64,BB"), e.map { (it as ChatEvent.Image).dataUrl })
    }

    @Test
    fun garbageChunkIsIgnored() {
        assertTrue(events("""{"id":"x","object":"chat.completion.chunk"}""").isEmpty())
    }

    @Test
    fun requestShape() {
        val req = ChatRequest(
            model = "m",
            messages = listOf(
                Message("1", Role.USER, "look", images = listOf("data:image/jpeg;base64,QQ")),
                Message("2", Role.ASSISTANT, "ok", reasoningDetails = """[{"type":"reasoning.text","text":"t"}]"""),
            ),
            systemPrompt = "be brief",
            maxTokens = 100,
            thinking = ThinkingLevel.HIGH,
        )
        val w = OpenRouterJson.toWire(req)
        assertEquals("m", w.getString("model"))
        assertTrue(w.getBoolean("stream"))
        assertTrue(w.getJSONObject("usage").getBoolean("include"))
        assertEquals("high", w.getJSONObject("reasoning").getString("effort"))
        val msgs = w.getJSONArray("messages")
        assertEquals("system", msgs.getJSONObject(0).getString("role"))
        val user = msgs.getJSONObject(1).getJSONArray("content")
        assertEquals("text", user.getJSONObject(0).getString("type"))
        assertEquals("data:image/jpeg;base64,QQ", user.getJSONObject(1).getJSONObject("image_url").getString("url"))
        val asst = msgs.getJSONObject(2)
        assertEquals("t", asst.getJSONArray("reasoning_details").getJSONObject(0).getString("text"))
        assertFalse(w.has("tools"))
        assertFalse(w.has("modalities"))
    }

    @Test
    fun latencyPreferenceBecomesProviderSort() {
        val w = OpenRouterJson.toWire(ChatRequest("m", emptyList(), preferLatency = true))
        assertEquals("latency", w.getJSONObject("provider").getString("sort"))
    }

    @Test
    fun thinkingOffOmitsReasoning() {
        val w = OpenRouterJson.toWire(ChatRequest("m", emptyList(), thinking = ThinkingLevel.OFF))
        assertNull(w.opt("reasoning"))
    }

    @Test
    fun thinkingOffSwitchesOffAModelThatThinksByDefault() {
        val info = model(ReasoningInfo(mandatory = false, defaultEnabled = true, supportedEfforts = listOf("max", "high", "low"), defaultEffort = "high"))
        val w = OpenRouterJson.toWire(ChatRequest("m", emptyList(), thinking = ThinkingLevel.OFF, modelInfo = info))
        assertFalse(w.getJSONObject("reasoning").getBoolean("enabled"))
        assertFalse(w.getJSONObject("reasoning").has("effort"))
    }

    @Test
    fun thinkingOffSendsNothingWhenTheModelCannotStop() {
        val info = model(ReasoningInfo(mandatory = true, defaultEnabled = true, supportedEfforts = listOf("high", "medium", "low"), defaultEffort = "medium"))
        val w = OpenRouterJson.toWire(ChatRequest("m", emptyList(), thinking = ThinkingLevel.OFF, modelInfo = info))
        assertNull(w.opt("reasoning"))
    }

    @Test
    fun effortSnapsToWhatTheModelAccepts() {
        val info = model(ReasoningInfo(mandatory = false, defaultEnabled = true, supportedEfforts = listOf("max", "high", "low"), defaultEffort = "high"))
        val w = OpenRouterJson.toWire(ChatRequest("m", emptyList(), thinking = ThinkingLevel.MEDIUM, modelInfo = info))
        assertEquals("low", w.getJSONObject("reasoning").getString("effort"))
        val x = OpenRouterJson.toWire(ChatRequest("m", emptyList(), thinking = ThinkingLevel.XHIGH, modelInfo = info))
        assertEquals("high", x.getJSONObject("reasoning").getString("effort"))
    }

    @Test
    fun noReasoningSentToAModelWithoutIt() {
        val info = ModelInfo("m", "m", 0, 0.0, 0.0, 0.0, supportsReasoning = false, supportsTools = true, inputModalities = setOf("text"), outputModalities = setOf("text"))
        val w = OpenRouterJson.toWire(ChatRequest("m", emptyList(), thinking = ThinkingLevel.HIGH, modelInfo = info))
        assertNull(w.opt("reasoning"))
    }

    private fun model(r: ReasoningInfo) = ModelInfo("m", "m", 0, 0.0, 0.0, 0.0, true, true, setOf("text"), setOf("text"), r)
}
