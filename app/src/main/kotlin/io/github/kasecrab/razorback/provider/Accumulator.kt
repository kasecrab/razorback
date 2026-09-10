package io.github.kasecrab.razorback.provider

import io.github.kasecrab.razorback.model.ToolCall
import io.github.kasecrab.razorback.model.Usage

/** Folds a stream of deltas into one reply; tool calls arrive by index and are merged. */
class Accumulator {

    val text = StringBuilder()
    val reasoning = StringBuilder()
    val reasoningDetails = ArrayList<String>()
    val images = ArrayList<String>()
    var usage: Usage? = null
    var finishReason: String? = null
    var generationId: String? = null

    private class Call(var id: String, val name: StringBuilder, val arguments: StringBuilder)

    private val calls = ArrayList<Call>()

    val hasToolCalls: Boolean get() = calls.isNotEmpty()

    fun apply(e: ChatEvent) {
        when (e) {
            is ChatEvent.Started -> generationId = e.generationId
            is ChatEvent.Text -> text.append(e.text)
            is ChatEvent.Reasoning -> reasoning.append(e.text)
            is ChatEvent.ReasoningDetail -> reasoningDetails.add(e.raw)
            is ChatEvent.Image -> if (e.dataUrl !in images) images.add(e.dataUrl)
            is ChatEvent.ToolCallDelta -> {
                while (calls.size <= e.index) calls.add(Call("", StringBuilder(), StringBuilder()))
                val c = calls[e.index]
                if (!e.id.isNullOrEmpty()) c.id = e.id
                if (!e.name.isNullOrEmpty()) c.name.append(e.name)
                c.arguments.append(e.arguments)
            }
            is ChatEvent.UsageReport -> usage = e.usage
            is ChatEvent.Finish -> finishReason = e.reason
            is ChatEvent.Failure -> {}
        }
    }

    /** Missing ids and blank argument strings are normalised so results can always be paired. */
    fun toolCalls(): List<ToolCall> = calls.mapIndexed { i, c ->
        ToolCall(
            id = c.id.ifEmpty { "call_$i" },
            name = c.name.toString(),
            arguments = c.arguments.toString().ifBlank { "{}" },
        )
    }

    fun reasoningDetailsJson(): String? =
        if (reasoningDetails.isEmpty()) null else reasoningDetails.joinToString(",", "[", "]")
}
