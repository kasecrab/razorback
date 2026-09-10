package io.github.kasecrab.razorback.provider

import io.github.kasecrab.razorback.core.Http
import io.github.kasecrab.razorback.core.Log
import io.github.kasecrab.razorback.data.ModelCache
import io.github.kasecrab.razorback.model.ModelInfo
import io.github.kasecrab.razorback.provider.openrouter.OpenRouter
import io.github.kasecrab.razorback.provider.openrouter.OpenRouterModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Every model the provider offers, from cache first and the network when the cache is a day old. */
class ModelCatalog(private val cache: ModelCache, private val key: () -> String?) {

    @Volatile var models: List<ModelInfo> = emptyList()
        private set
    @Volatile var fetchedAt: Long = 0L
        private set

    private val byId = HashMap<String, ModelInfo>()
    private val listeners = ArrayList<() -> Unit>(2)
    private var loaded = false

    fun onChange(l: () -> Unit) {
        listeners.add(l)
    }

    fun removeOnChange(l: () -> Unit) {
        listeners.remove(l)
    }

    fun find(id: String): ModelInfo? = byId[id]

    /** Loads from cache, then refreshes if stale or [force]. Safe to call often. Main thread. */
    suspend fun load(force: Boolean = false): Throwable? {
        if (!loaded) {
            loaded = true
            withContext(Dispatchers.IO) { cache.read() }?.let { publish(it.models, it.fetchedAt) }
        }
        val stale = System.currentTimeMillis() - fetchedAt > ModelCache.MAX_AGE_MS
        if (!force && !stale && models.isNotEmpty()) return null
        return try {
            val raw = withContext(Dispatchers.IO) {
                val headers = key()?.let { OpenRouter.headers(it) } ?: emptyMap()
                val json = try {
                    Http.getJson("${OpenRouter.BASE}/models?output_modalities=all", headers)
                } catch (_: IOException) {
                    Http.getJson("${OpenRouter.BASE}/models", headers)
                }
                cache.write(json)
                json
            }
            publish(OpenRouterModels.parse(raw), System.currentTimeMillis())
            null
        } catch (e: Exception) {
            Log.w("model list refresh failed: ${e.message}")
            e
        }
    }

    private fun publish(list: List<ModelInfo>, at: Long) {
        models = list
        fetchedAt = at
        byId.clear()
        for (m in list) byId[m.id] = m
        for (l in listeners) l()
    }
}
