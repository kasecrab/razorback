package io.github.kasecrab.razorback.model

enum class Role(val wire: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool"),
    ;

    companion object {
        fun fromWire(s: String?): Role = entries.firstOrNull { it.wire == s } ?: USER
    }
}

enum class MessageStatus { STREAMING, COMPLETE, CUT, ERROR }

class ToolCall(val id: String, val name: String, val arguments: String)

/**
 * One turn of the conversation. Mutable fields change only on the main thread while a
 * reply streams; everything else is fixed at creation.
 */
class Message(
    val id: String,
    val role: Role,
    var content: String = "",
    var reasoning: String? = null,
    /** Raw JSON array from the provider, echoed back untouched on later turns. */
    var reasoningDetails: String? = null,
    var toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    /** Data URLs of images attached (user) or generated (assistant). */
    var images: List<String> = emptyList(),
    val model: String? = null,
    var status: MessageStatus = MessageStatus.COMPLETE,
    var error: String? = null,
    var usage: Usage? = null,
    val createdAt: Long = System.currentTimeMillis(),
    var finishedAt: Long? = null,
    var firstTokenAt: Long? = null,
    /** When the answer began after the reasoning, to show how long the model thought. */
    var reasoningEndedAt: Long? = null,
)
