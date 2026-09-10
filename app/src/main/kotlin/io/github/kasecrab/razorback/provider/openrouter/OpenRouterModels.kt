package io.github.kasecrab.razorback.provider.openrouter

import io.github.kasecrab.razorback.core.Http
import io.github.kasecrab.razorback.core.arr
import io.github.kasecrab.razorback.core.dbl
import io.github.kasecrab.razorback.core.forEachObject
import io.github.kasecrab.razorback.core.int
import io.github.kasecrab.razorback.core.obj
import io.github.kasecrab.razorback.core.str
import io.github.kasecrab.razorback.core.strings
import io.github.kasecrab.razorback.model.ModelInfo
import org.json.JSONObject
import java.io.IOException

object OpenRouterModels {

    fun fetch(key: String?): List<ModelInfo> {
        val headers = if (key != null) OpenRouter.headers(key) else emptyMap()
        val json = try {
            Http.getJson("${OpenRouter.BASE}/models?output_modalities=all", headers)
        } catch (_: IOException) {
            Http.getJson("${OpenRouter.BASE}/models", headers)
        }
        return parse(json)
    }

    fun parse(json: JSONObject): List<ModelInfo> {
        val out = ArrayList<ModelInfo>(400)
        json.arr("data")?.forEachObject { m ->
            val id = m.str("id") ?: return@forEachObject
            val pricing = m.obj("pricing")
            val params = m.arr("supported_parameters")?.strings() ?: emptyList()
            val arch = m.obj("architecture")
            out.add(
                ModelInfo(
                    id = id,
                    name = m.str("name") ?: id,
                    contextLength = m.int("context_length") ?: 0,
                    promptPerM = perMillion(pricing?.dbl("prompt")),
                    completionPerM = perMillion(pricing?.dbl("completion")),
                    imageOutPerM = perMillion(pricing?.dbl("image_output") ?: pricing?.dbl("image_token")),
                    supportsReasoning = "reasoning" in params || "include_reasoning" in params,
                    supportsTools = "tools" in params,
                    inputModalities = arch?.arr("input_modalities")?.strings()?.toSet() ?: setOf("text"),
                    outputModalities = arch?.arr("output_modalities")?.strings()?.toSet() ?: setOf("text"),
                ),
            )
        }
        out.sortBy { it.id }
        return out
    }

    private fun perMillion(perToken: Double?): Double = (perToken ?: 0.0) * 1_000_000.0
}
