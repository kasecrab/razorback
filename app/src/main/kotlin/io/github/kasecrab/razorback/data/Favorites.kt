package io.github.kasecrab.razorback.data

import io.github.kasecrab.razorback.core.Keys
import io.github.kasecrab.razorback.core.Prefs
import io.github.kasecrab.razorback.core.forEachObject
import io.github.kasecrab.razorback.core.jsonObject
import io.github.kasecrab.razorback.core.str
import io.github.kasecrab.razorback.model.ThinkingLevel
import org.json.JSONArray
import org.json.JSONException

class Favorite(val id: String, val thinking: ThinkingLevel?)

/** Starred models, in the order they were starred, each with an optional pinned thinking level. */
class Favorites(private val prefs: Prefs) {

    private val items = ArrayList<Favorite>()
    private val listeners = ArrayList<() -> Unit>(2)

    init {
        reload()
    }

    /** Re-read from preferences, after an import. */
    fun reload() {
        items.clear()
        try {
            JSONArray(prefs[Keys.FAVORITES].ifEmpty { "[]" }).forEachObject { o ->
                val id = o.str("id") ?: return@forEachObject
                items.add(Favorite(id, o.str("thinking")?.let { ThinkingLevel.fromName(it) }))
            }
        } catch (_: JSONException) {
        }
        for (l in listeners) l()
    }

    val all: List<Favorite> get() = items

    fun onChange(l: () -> Unit) {
        listeners.add(l)
    }

    fun removeOnChange(l: () -> Unit) {
        listeners.remove(l)
    }

    fun contains(id: String): Boolean = items.any { it.id == id }

    fun get(id: String): Favorite? = items.firstOrNull { it.id == id }

    fun toggle(id: String): Boolean {
        val had = items.removeAll { it.id == id }
        if (!had) items.add(Favorite(id, null))
        save()
        return !had
    }

    fun setThinking(id: String, level: ThinkingLevel?) {
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return
        items[i] = Favorite(id, level)
        save()
    }

    fun move(from: Int, to: Int) {
        if (from == to || from !in items.indices || to !in items.indices) return
        items.add(to, items.removeAt(from))
        save()
    }

    private fun save() {
        val arr = JSONArray()
        for (f in items) arr.put(jsonObject { put("id", f.id); f.thinking?.let { put("thinking", it.name) } })
        prefs[Keys.FAVORITES] = arr.toString()
        for (l in listeners) l()
    }
}
