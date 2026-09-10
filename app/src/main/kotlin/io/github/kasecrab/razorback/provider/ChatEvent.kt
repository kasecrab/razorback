package io.github.kasecrab.razorback.provider

import io.github.kasecrab.razorback.model.Usage

/** What a provider stream yields, already normalised away from any vendor's wire shape. */
sealed class ChatEvent {
    class Started(val generationId: String?) : ChatEvent()
    class Text(val text: String) : ChatEvent()
    class Reasoning(val text: String) : ChatEvent()
    /** Raw JSON of one reasoning_details entry, kept for round-tripping. */
    class ReasoningDetail(val raw: String) : ChatEvent()
    class ToolCallDelta(val index: Int, val id: String?, val name: String?, val arguments: String) : ChatEvent()
    class Image(val dataUrl: String) : ChatEvent()
    class UsageReport(val usage: Usage) : ChatEvent()
    class Finish(val reason: String) : ChatEvent()
    class Failure(val status: Int, val message: String) : ChatEvent()
}
