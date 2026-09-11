package io.github.kasecrab.razorback.provider.openrouter

import io.github.kasecrab.razorback.core.Http
import io.github.kasecrab.razorback.core.arr
import io.github.kasecrab.razorback.core.bool
import io.github.kasecrab.razorback.core.dbl
import io.github.kasecrab.razorback.core.forEachObject
import io.github.kasecrab.razorback.core.int
import io.github.kasecrab.razorback.core.long
import io.github.kasecrab.razorback.core.obj
import io.github.kasecrab.razorback.core.str
import io.github.kasecrab.razorback.core.strings
import io.github.kasecrab.razorback.model.ModelInfo
import io.github.kasecrab.razorback.model.ReasoningInfo
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
            val reasoning = m.obj("reasoning")?.let {
                ReasoningInfo(
                    mandatory = it.bool("mandatory") ?: false,
                    defaultEnabled = it.bool("default_enabled") ?: false,
                    supportedEfforts = it.arr("supported_efforts")?.strings(),
                    defaultEffort = it.str("default_effort"),
                )
            }
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
                    reasoning = reasoning,
                    created = m.long("created") ?: 0L,
                    intelligence = m.obj("benchmarks")?.obj("artificial_analysis")?.dbl("intelligence_index"),
                ),
            )
        }
        out.sortBy { it.id }
        return out
    }

    /** The router lists a category's models most used first; only the order is kept. */
    fun parseIds(json: JSONObject): List<String> {
        val out = ArrayList<String>(20)
        json.arr("data")?.forEachObject { m -> m.str("id")?.let { out.add(it) } }
        return out
    }

    private fun perMillion(perToken: Double?): Double = (perToken ?: 0.0) * 1_000_000.0
}
