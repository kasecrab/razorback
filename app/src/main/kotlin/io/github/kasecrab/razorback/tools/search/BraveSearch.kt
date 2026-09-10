package io.github.kasecrab.razorback.tools.search

import io.github.kasecrab.razorback.core.Http
import io.github.kasecrab.razorback.core.arr
import io.github.kasecrab.razorback.core.forEachObject
import io.github.kasecrab.razorback.core.obj
import io.github.kasecrab.razorback.core.str
import java.net.URLEncoder

class SearchHit(val title: String, val url: String, val snippet: String)

object BraveSearch {
    fun search(key: String, query: String, count: Int): List<SearchHit> {
        val url = "https://api.search.brave.com/res/v1/web/search?q=" + URLEncoder.encode(query, "UTF-8") + "&count=$count&text_decorations=false"
        val json = Http.getJson(url, mapOf("X-Subscription-Token" to key, "Accept" to "application/json"))
        val out = ArrayList<SearchHit>(count)
        json.obj("web")?.arr("results")?.forEachObject { r ->
            val extra = r.arr("extra_snippets")?.let { a -> (0 until a.length()).joinToString(" ") { a.optString(it) } } ?: ""
            out.add(SearchHit(r.str("title") ?: "", r.str("url") ?: "", ((r.str("description") ?: "") + " " + extra).trim()))
        }
        return out
    }
}
