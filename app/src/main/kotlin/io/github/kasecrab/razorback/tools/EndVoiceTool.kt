package io.github.kasecrab.razorback.tools

import io.github.kasecrab.razorback.core.jsonObject
import org.json.JSONObject

/**
 * Offered only on spoken turns: the model calls it when the person asks to stop talking,
 * and the voice screen closes once the goodbye has been said. The tool itself only
 * raises a hand; the session on the main thread does the closing.
 */
class EndVoiceTool : Tool {

    /** Set by the voice session while it is live; called on an IO thread. */
    @Volatile var onCalled: (() -> Unit)? = null

    override val name: String = NAME

    override val spec: JSONObject = jsonObject {
        put("type", "function")
        put("function", jsonObject {
            put("name", name)
            put(
                "description",
                "Ends the voice conversation and closes the voice screen. Call it when the person asks to stop, close, exit or end " +
                    "voice mode or the voice chat, asks to close the screen, or says they are done or goodbye. " +
                    "Say a short goodbye in the same reply; the screen closes once you finish speaking.",
            )
            put("parameters", jsonObject {
                put("type", "object")
                put("properties", jsonObject {})
            })
        })
    }

    override fun run(args: JSONObject): ToolResult {
        val handler = onCalled ?: return ToolResult("Voice mode is not open.", isError = true)
        handler()
        return ToolResult("Voice mode will close once you finish speaking. Say a short goodbye.")
    }

    companion object {
        const val NAME = "end_voice_mode"
    }
}
