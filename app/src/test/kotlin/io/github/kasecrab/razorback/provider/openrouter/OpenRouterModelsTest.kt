package io.github.kasecrab.razorback.provider.openrouter

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterModelsTest {
    @Test
    fun parsesPricingPerMillionAndCapabilities() {
        val json = JSONObject(
            """{"data":[{"id":"z/b","name":"B","context_length":128000,"pricing":{"prompt":"0.0000015","completion":"0.000006"},
            "supported_parameters":["tools","reasoning"],"architecture":{"input_modalities":["text","image"],"output_modalities":["text"]},
            "reasoning":{"mandatory":false,"default_enabled":true,"supported_efforts":["max","high","low"],"default_effort":"high"},
            "created":1788552838,"benchmarks":{"artificial_analysis":{"intelligence_index":52.8}}},
            {"id":"a/a","pricing":{"prompt":"0","completion":"0"}},{"name":"no id"}]}""",
        )
        val models = OpenRouterModels.parse(json)
        assertEquals(listOf("a/a", "z/b"), models.map { it.id })
        val b = models[1]
        assertEquals(1.5, b.promptPerM, 1e-9)
        assertEquals(6.0, b.completionPerM, 1e-9)
        assertTrue(b.supportsTools && b.supportsReasoning && b.acceptsImages)
        assertTrue(models[0].isFree)
        assertEquals("a", models[0].shortName)
        val r = b.reasoning!!
        assertTrue(r.defaultEnabled && !r.mandatory)
        assertEquals(listOf("max", "high", "low"), r.supportedEfforts)
        assertEquals("high", r.defaultEffort)
        assertEquals(null, models[0].reasoning)
        assertEquals(1788552838L, b.created)
        assertEquals(52.8, b.intelligence!!, 1e-9)
        assertEquals(null, models[0].intelligence)
    }

    @Test
    fun aCategoryListKeepsOnlyTheIdsInTheRoutersOrder() {
        val json = JSONObject("""{"data":[{"id":"z/b","name":"B"},{"name":"no id"},{"id":"a/a"}]}""")
        assertEquals(listOf("z/b", "a/a"), OpenRouterModels.parseIds(json))
    }
}
