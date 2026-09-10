package io.github.kasecrab.razorback.provider

import io.github.kasecrab.razorback.model.Message
import io.github.kasecrab.razorback.model.ModelInfo
import io.github.kasecrab.razorback.model.ThinkingLevel
import org.json.JSONObject

class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val systemPrompt: String? = null,
    val maxTokens: Int? = null,
    val temperature: Float? = null,
    val thinking: ThinkingLevel = ThinkingLevel.OFF,
    /** What is known about the model, so thinking can be phrased the way it understands. */
    val modelInfo: ModelInfo? = null,
    val tools: List<JSONObject> = emptyList(),
    val imageOutput: Boolean = false,
    /** Ask the router to prefer the fastest provider; spoken replies care more about the first word than the price. */
    val preferLatency: Boolean = false,
)
