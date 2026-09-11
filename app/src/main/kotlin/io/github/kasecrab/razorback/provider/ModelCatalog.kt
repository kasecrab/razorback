package io.github.kasecrab.razorback.provider

import io.github.kasecrab.razorback.core.Http
import io.github.kasecrab.razorback.core.Log
import io.github.kasecrab.razorback.data.ModelCache
import io.github.kasecrab.razorback.model.ModelInfo
import io.github.kasecrab.razorback.provider.openrouter.OpenRouter
import io.github.kasecrab.razorback.provider.openrouter.OpenRouterModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException

/** Every model the provider offers, from cache first and the network when the cache is a day old. */
class ModelCatalog(private val cache: ModelCache, private val key: () -> String?) {

    @Volatile var models: List<ModelInfo> = emptyList()
        private set
    @Volatile var fetchedAt: Long = 0L
        private set

    /** Ids of the models the router sees used most for programming, most used first. */
    @Volatile var popular: List<String> = emptyList()
        private set
    @Volatile var popularAt: Long = 0L
        private set
    private var popularLoaded = false

    /** Output tokens per second by model id, for the models that have been measured. */
    @Volatile var speeds: Map<String, Double> = emptyMap()
        private set
    @Volatile var speedsAt: Long = 0L
        private set
    /** How far a measurement in progress has got: done and total; both zero when idle. */
    @Volatile var speedsDone: Int = 0
        private set
    @Volatile var speedsTotal: Int = 0
        private set
    private var speedsLoaded = false
    private var measuring = false

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

    /** The most used list: from cache, then the router when the cache is a day old or [force]. Main thread. */
    suspend fun loadPopular(force: Boolean = false): Throwable? {
        if (!popularLoaded) {
            popularLoaded = true
            withContext(Dispatchers.IO) { cache.readPopular() }?.let {
                popular = it.ids
                popularAt = it.fetchedAt
            }
        }
        val stale = System.currentTimeMillis() - popularAt > ModelCache.MAX_AGE_MS
        if (!force && !stale && popular.isNotEmpty()) return null
        return try {
            val ids = withContext(Dispatchers.IO) {
                val headers = key()?.let { OpenRouter.headers(it) } ?: emptyMap()
                val json = Http.getJson("${OpenRouter.BASE}/models?category=$POPULAR_CATEGORY", headers)
                cache.writePopular(json)
                OpenRouterModels.parseIds(json)
            }
            popular = ids
            popularAt = System.currentTimeMillis()
            for (l in listeners) l()
            null
        } catch (e: Exception) {
            Log.w("popular models refresh failed: ${e.message}")
            e
        }
    }

    /**
     * Measures [ids] one endpoints page each, a few at a time, publishing as results land.
     * Cached speeds show at once and are re-measured when a day old. Main thread; one
     * measurement runs at a time, and cancelling the caller stops it.
     */
    suspend fun loadSpeeds(ids: List<String>, force: Boolean = false): Throwable? {
        if (!speedsLoaded) {
            speedsLoaded = true
            withContext(Dispatchers.IO) { cache.readSpeeds() }?.let {
                speeds = it.speeds
                speedsAt = it.fetchedAt
            }
        }
        if (measuring) return null
        val stale = System.currentTimeMillis() - speedsAt > ModelCache.MAX_AGE_MS
        val wanted = if (force || stale) ids else ids.filter { it !in speeds }
        if (wanted.isEmpty()) return null
        measuring = true
        speedsDone = 0
        speedsTotal = wanted.size
        val results = HashMap(speeds)
        var failure: Throwable? = null
        try {
            val headers = key()?.let { OpenRouter.headers(it) } ?: emptyMap()
            val lanes = Dispatchers.IO.limitedParallelism(SPEED_LANES)
            for (chunk in wanted.chunked(SPEED_CHUNK)) {
                val got = coroutineScope {
                    chunk.map { id ->
                        async(lanes) {
                            id to runCatching { OpenRouterModels.parseSpeed(Http.getJson("${OpenRouter.BASE}/models/$id/endpoints", headers)) }
                        }
                    }.awaitAll()
                }
                for ((id, r) in got) {
                    r.onSuccess { if (it != null) results[id] = it }
                    r.onFailure { failure = it }
                }
                speedsDone += chunk.size
                speeds = HashMap(results)
                for (l in listeners) l()
            }
            speedsAt = System.currentTimeMillis()
            withContext(Dispatchers.IO) { cache.writeSpeeds(results) }
        } finally {
            measuring = false
            speedsDone = 0
            speedsTotal = 0
            for (l in listeners) l()
        }
        if (failure != null && results.isEmpty()) Log.w("speed measurement failed: ${failure?.message}")
        return if (results.isEmpty()) failure else null
    }

    private fun publish(list: List<ModelInfo>, at: Long) {
        models = list
        fetchedAt = at
        byId.clear()
        for (m in list) byId[m.id] = m
        for (l in listeners) l()
    }

    companion object {
        /** The router ranks usage per category; programming is the one this app's people live in. */
        const val POPULAR_CATEGORY = "programming"
        /** Endpoint pages fetched at once while measuring speed, and how many make a batch. */
        const val SPEED_LANES = 4
        const val SPEED_CHUNK = 8
    }
}
