package io.github.kasecrab.razorback.voice

import io.github.kasecrab.razorback.core.Http
import io.github.kasecrab.razorback.core.Log
import io.github.kasecrab.razorback.core.arr
import io.github.kasecrab.razorback.core.forEachObject
import io.github.kasecrab.razorback.core.long
import io.github.kasecrab.razorback.core.obj
import io.github.kasecrab.razorback.core.str
import io.github.kasecrab.razorback.core.strings
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/**
 * Deepgram's voice list, read from its model catalogue so new voices appear and retired
 * ones go without an app update. The last reply is kept on disk; nothing is hardcoded
 * beyond the id of the default voice.
 */
class VoiceCatalog(dir: File, private val key: () -> String?) {

    private val file = File(dir, "voices.json")

    @Volatile private var cached: List<Voice>? = null
    @Volatile private var fetchedAt = 0L

    /** What is known now: the last fetched list, or nothing before the first fetch. */
    val voices: List<Voice>
        get() = cached ?: read().also { cached = it }

    val isStale: Boolean get() = voices.isEmpty() || System.currentTimeMillis() - fetchedAt > MAX_AGE_MS

    fun byId(id: String): Voice? = voices.firstOrNull { it.id == id }

    /** Fetches the catalogue; true when the list differs from what was cached. */
    suspend fun refresh(): Boolean {
        val apiKey = key() ?: return false
        val json = withContext(Dispatchers.IO) { Http.getJson(URL, mapOf("Authorization" to "Token $apiKey")) }
        val fresh = parse(json)
        if (fresh.isEmpty()) return false
        val changed = fresh.map { it.id } != voices.map { it.id }
        cached = fresh
        fetchedAt = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            try {
                val keep = JSONObject().put("tts", json.arr("tts")).put("fetched_ms", fetchedAt)
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(keep.toString())
                tmp.renameTo(file)
            } catch (e: Exception) {
                Log.w("voice catalogue not saved", e)
            }
        }
        return changed
    }

    /** Languages present, most voices first, as code to display name in the phone's language. */
    fun languages(): List<Pair<String, String>> =
        voices.groupingBy { it.language }.eachCount().entries.sortedByDescending { it.value }.map { it.key to languageName(it.key) }

    /** Descriptors used by at least [MIN_TAG_VOICES] voices, most common first. */
    fun tones(): List<Pair<String, String>> =
        voices.flatMap { it.tags }.groupingBy { it }.eachCount().entries
            .filter { it.value >= MIN_TAG_VOICES }
            .sortedByDescending { it.value }
            .map { it.key to it.key.replaceFirstChar { c -> c.uppercase() } }

    private fun read(): List<Voice> {
        if (!file.exists()) return emptyList()
        return try {
            val json = JSONObject(file.readText())
            fetchedAt = json.long("fetched_ms") ?: 0L
            parse(json)
        } catch (e: JSONException) {
            Log.w("voice catalogue unreadable", e)
            emptyList()
        }
    }

    companion object {
        const val URL = "https://api.deepgram.com/v1/models"
        const val MAX_AGE_MS = 24L * 60 * 60 * 1000
        const val MIN_TAG_VOICES = 3

        /** "#rrggbb" to an opaque ARGB int, 0 for anything else. */
        fun hexColor(s: String): Int {
            if (s.length != 7 || s[0] != '#') return 0
            val rgb = s.substring(1).toIntOrNull(16) ?: return 0
            return rgb or (0xFF shl 24)
        }

        fun languageName(code: String): String {
            val name = Locale.forLanguageTag(code).displayLanguage
            return if (name.isEmpty() || name == code) code.uppercase() else name.replaceFirstChar { it.uppercase() }
        }

        /** Every `tts` entry with a canonical model name; anything missing in the metadata is left blank. */
        fun parse(json: JSONObject): List<Voice> {
            val out = ArrayList<Voice>()
            json.arr("tts")?.forEachObject { m ->
                // Only the canonical name is a model id the speak endpoint accepts.
                val id = m.str("canonical_name") ?: return@forEachObject
                val meta = m.obj("metadata")
                val tags = meta?.arr("tags")?.strings()?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() } ?: emptyList()
                val feminine = when {
                    "feminine" in tags -> true
                    "masculine" in tags -> false
                    else -> null
                }
                val languages = m.arr("languages")?.strings() ?: emptyList()
                val language = (languages.firstOrNull { !it.contains('-') } ?: languages.firstOrNull() ?: id.substringAfterLast('-')).substringBefore('-').lowercase()
                val color = meta?.str("color")?.let { hexColor(it) } ?: 0
                out.add(
                    Voice(
                        id = id,
                        name = meta?.str("display_name") ?: (m.str("name") ?: id).replaceFirstChar { it.uppercase() },
                        feminine = feminine,
                        accent = meta?.str("accent") ?: "",
                        age = meta?.str("age") ?: "",
                        tags = tags.filter { it != "feminine" && it != "masculine" },
                        language = language,
                        color = color,
                        sample = meta?.str("sample"),
                        architecture = m.str("architecture") ?: "",
                    ),
                )
            }
            // Newest architecture first, then by name, so Aura-2 leads and retired lines trail.
            out.sortWith(compareByDescending<Voice> { it.architecture }.thenBy { it.name })
            return out
        }
    }
}
