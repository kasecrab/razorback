package io.github.kasecrab.razorback.provider

import io.github.kasecrab.razorback.model.Message
import io.github.kasecrab.razorback.model.ThinkingLevel
import org.json.JSONObject

class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val systemPrompt: String? = null,
    val maxTokens: Int? = null,
    val temperature: Float? = null,
    val thinking: ThinkingLevel = ThinkingLevel.OFF,
    val tools: List<JSONObject> = emptyList(),
    val imageOutput: Boolean = false,
)
