package io.github.kasecrab.razorback.tools.search

import io.github.kasecrab.razorback.core.Http
import io.github.kasecrab.razorback.core.arr
import io.github.kasecrab.razorback.core.forEachObject
import io.github.kasecrab.razorback.core.jsonObject
import io.github.kasecrab.razorback.core.str

object ExaSearch {
    fun search(key: String, query: String, count: Int): List<SearchHit> {
        val body = jsonObject {
            put("query", query)
            put("numResults", count)
            put("type", "auto")
            put("contents", jsonObject { put("text", jsonObject { put("maxCharacters", 1500) }) })
        }
        val json = Http.postJson("https://api.exa.ai/search", mapOf("x-api-key" to key), body)
        val out = ArrayList<SearchHit>(count)
        json.arr("results")?.forEachObject { r ->
            out.add(SearchHit(r.str("title") ?: "", r.str("url") ?: "", (r.str("text") ?: "").trim()))
        }
        return out
    }
}
