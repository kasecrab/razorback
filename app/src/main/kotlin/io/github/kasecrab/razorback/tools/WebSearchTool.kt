package io.github.kasecrab.razorback.tools

import io.github.kasecrab.razorback.core.Log
import io.github.kasecrab.razorback.core.Secrets
import io.github.kasecrab.razorback.core.int
import io.github.kasecrab.razorback.core.jsonObject
import io.github.kasecrab.razorback.core.str
import io.github.kasecrab.razorback.tools.search.BraveSearch
import io.github.kasecrab.razorback.tools.search.ExaSearch
import io.github.kasecrab.razorback.tools.search.SearchHit
import org.json.JSONArray
import org.json.JSONObject

/**
 * `web_search(query, count)` backed by the chosen search vendor, with the other one as the
 * backup: it answers when the chosen vendor has no key or its call fails.
 */
class WebSearchTool(
    private val braveKey: () -> String?,
    private val exaKey: () -> String?,
    private val preferred: () -> String,
) : Tool {

    override val name: String = "web_search"

    override val spec: JSONObject = jsonObject {
        put("type", "function")
        put("function", jsonObject {
            put("name", name)
            put("description", "Search the web for current information. Returns titles, URLs and snippets. Use it for anything after your training data, live facts, prices, news, docs.")
            put("parameters", jsonObject {
                put("type", "object")
                put("properties", jsonObject {
                    put("query", jsonObject { put("type", "string"); put("description", "What to search for, as a short web query") })
                    put("count", jsonObject { put("type", "integer"); put("minimum", 1); put("maximum", 10); put("description", "How many results, default 5") })
                })
                put("required", JSONArray().put("query"))
            })
        })
    }

    val available: Boolean get() = braveKey() != null || exaKey() != null

    override fun run(args: JSONObject): ToolResult {
        val query = args.str("query")?.trim().orEmpty()
        if (query.isEmpty()) return ToolResult("query is required", isError = true)
        val count = (args.int("count") ?: 5).coerceIn(1, 10)
        val order = if (preferred() == Secrets.EXA) listOf(Secrets.EXA, Secrets.BRAVE) else listOf(Secrets.BRAVE, Secrets.EXA)
        var failure: Exception? = null
        for (vendor in order) {
            val key = (if (vendor == Secrets.BRAVE) braveKey() else exaKey()) ?: continue
            try {
                val hits = if (vendor == Secrets.BRAVE) BraveSearch.search(key, query, count) else ExaSearch.search(key, query, count)
                return ToolResult(format(hits))
            } catch (e: Exception) {
                Log.w("$vendor search failed: ${e.message}")
                failure = e
            }
        }
        val why = failure ?: return ToolResult("no search key configured", isError = true)
        return ToolResult("search failed: ${why.message}", isError = true)
    }

    private fun format(hits: List<SearchHit>): String {
        if (hits.isEmpty()) return "no results"
        val sb = StringBuilder()
        for ((i, h) in hits.withIndex()) {
            sb.append(i + 1).append(". ").append(h.title).append('\n').append(h.url).append('\n')
            if (h.snippet.isNotEmpty()) sb.append(h.snippet.take(1200)).append('\n')
            sb.append('\n')
            if (sb.length > MAX_CHARS) break
        }
        return if (sb.length > MAX_CHARS) sb.substring(0, MAX_CHARS) + "…" else sb.toString().trimEnd()
    }

    private companion object {
        const val MAX_CHARS = 8000
    }
}
