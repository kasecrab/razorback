package io.github.kasecrab.razorback.ui.models

import io.github.kasecrab.razorback.model.ModelInfo

/**
 * The lists the model browser opens on, so a person finds a model without knowing its
 * name. Batch variants never appear: they are not for chatting. Ranked lists also leave
 * out the other routing variants, so a model is listed once.
 */
object Ranking {

    enum class Tab { POPULAR, SMARTEST, VALUE, FREE, NEW, ALL }

    /** Everything a chat can use, A to Z by id. */
    fun all(models: List<ModelInfo>): List<ModelInfo> = models.filter { !it.isBatch }

    /** The router's most used, in its order; ids it names that the catalogue lacks are skipped. */
    fun popular(models: List<ModelInfo>, ids: List<String>): List<ModelInfo> {
        val byId = HashMap<String, ModelInfo>(models.size * 2)
        for (m in models) byId[m.id] = m
        return ids.mapNotNull { byId[it] }
    }

    /** Scored models, highest intelligence index first. */
    fun smartest(models: List<ModelInfo>): List<ModelInfo> = scored(models).sortedByDescending { it.intelligence }

    /**
     * Intelligence per dollar among the smarter half of the scored, paid models, so a tiny
     * model that costs nothing and knows nothing cannot top the list. The dollar is the
     * usual blend of three input tokens to one output token.
     */
    fun value(models: List<ModelInfo>): List<ModelInfo> {
        val paid = scored(models).filter { !it.isFree }
        if (paid.isEmpty()) return paid
        val sorted = paid.map { it.intelligence!! }.sorted()
        val floor = sorted[(sorted.size - 1) / 2]
        return paid.filter { it.intelligence!! >= floor }.sortedByDescending { it.intelligence!! / blendedPrice(it) }
    }

    /** Costs nothing; the scored ones first, by score, then the rest by name. */
    fun free(models: List<ModelInfo>): List<ModelInfo> =
        models.filter { it.isFree && !it.isBatch }.sortedWith(compareByDescending<ModelInfo> { it.intelligence ?: -1.0 }.thenBy { it.name })

    /** Newest listing first. */
    fun newest(models: List<ModelInfo>): List<ModelInfo> = all(models).sortedByDescending { it.created }

    fun blendedPrice(m: ModelInfo): Double = maxOf((3 * m.promptPerM + m.completionPerM) / 4, 0.01)

    private fun scored(models: List<ModelInfo>): List<ModelInfo> = models.filter { it.intelligence != null && !it.id.contains(':') }
}
