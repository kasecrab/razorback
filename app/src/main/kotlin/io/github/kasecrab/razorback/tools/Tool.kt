package io.github.kasecrab.razorback.tools

import org.json.JSONObject

class ToolResult(val output: String, val isError: Boolean = false)

/** Something the model can call. [spec] is the OpenAI-style function definition. */
interface Tool {
    val name: String
    val spec: JSONObject

    /** Runs on an IO thread; must never throw, errors come back as [ToolResult.isError]. */
    fun run(args: JSONObject): ToolResult
}
