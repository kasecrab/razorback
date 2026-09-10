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
            "supported_parameters":["tools","reasoning"],"architecture":{"input_modalities":["text","image"],"output_modalities":["text"]}},
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
    }
}
