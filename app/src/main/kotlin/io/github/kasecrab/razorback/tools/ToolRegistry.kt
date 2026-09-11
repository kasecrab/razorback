package io.github.kasecrab.razorback.tools

import io.github.kasecrab.razorback.core.Keys
import io.github.kasecrab.razorback.core.Prefs
import io.github.kasecrab.razorback.core.Secrets
import org.json.JSONException
import org.json.JSONObject

/** The tools offered on a request, given keys and switches; runs a call by name. */
class ToolRegistry(prefs: Prefs, secrets: Secrets) {

    private val webSearch = WebSearchTool({ secrets.get(Secrets.BRAVE) }, { secrets.get(Secrets.EXA) }, { prefs[Keys.SEARCH_PROVIDER] })
    private val prefs = prefs

    fun enabled(): List<Tool> {
        val out = ArrayList<Tool>(1)
        if (prefs[Keys.WEB_SEARCH] && webSearch.available) out.add(webSearch)
        return out
    }

    fun run(name: String, arguments: String): ToolResult {
        val tool = enabled().firstOrNull { it.name == name } ?: return ToolResult("unknown tool $name", isError = true)
        val args = try {
            JSONObject(arguments.ifBlank { "{}" })
        } catch (e: JSONException) {
            return ToolResult("arguments were not valid JSON", isError = true)
        }
        return tool.run(args)
    }
}
