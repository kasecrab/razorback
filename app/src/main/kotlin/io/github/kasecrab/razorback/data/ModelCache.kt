package io.github.kasecrab.razorback.data

import io.github.kasecrab.razorback.core.Log
import io.github.kasecrab.razorback.core.long
import io.github.kasecrab.razorback.model.ModelInfo
import io.github.kasecrab.razorback.provider.openrouter.OpenRouterModels
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/** The provider's raw model lists on disk, so the picker works offline and starts instantly. */
class ModelCache(dir: File) {

    private val file = File(dir, "models.json")
    private val popularFile = File(dir, "popular.json")

    class Entry(val models: List<ModelInfo>, val fetchedAt: Long)

    class Popular(val ids: List<String>, val fetchedAt: Long)

    fun readPopular(): Popular? {
        if (!popularFile.exists()) return null
        return try {
            val json = JSONObject(popularFile.readText())
            Popular(OpenRouterModels.parseIds(json), json.long("fetched_ms") ?: 0L)
        } catch (e: JSONException) {
            Log.w("popular cache unreadable", e)
            null
        }
    }

    fun writePopular(raw: JSONObject) {
        raw.put("fetched_ms", System.currentTimeMillis())
        val tmp = File(popularFile.parentFile, popularFile.name + ".tmp")
        tmp.writeText(raw.toString())
        tmp.renameTo(popularFile)
    }

    fun read(): Entry? {
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            if (json.optInt("version") != VERSION) return null
            Entry(OpenRouterModels.parse(json), json.long("fetched_ms") ?: 0L)
        } catch (e: JSONException) {
            Log.w("model cache unreadable", e)
            null
        }
    }

    fun write(raw: JSONObject) {
        raw.put("version", VERSION)
        raw.put("fetched_ms", System.currentTimeMillis())
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(raw.toString())
        tmp.renameTo(file)
    }

    companion object {
        const val VERSION = 1
        const val MAX_AGE_MS = 24L * 60 * 60 * 1000
    }
}
