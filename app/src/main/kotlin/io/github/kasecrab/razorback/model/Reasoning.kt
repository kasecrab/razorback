package io.github.kasecrab.razorback.model

/**
 * Turns a chosen [ThinkingLevel] into what a particular model can actually do. A model
 * that thinks unless told otherwise needs an explicit off; a model that always thinks
 * cannot be switched off; a model with a short list of effort words gets the nearest one.
 */
object Reasoning {

    /** The level the model will really run at, or null when it will not reason. */
    fun effective(level: ThinkingLevel, info: ModelInfo?): ThinkingLevel? {
        if (info != null && !info.supportsReasoning) return null
        val r = info?.reasoning
        if (level == ThinkingLevel.OFF) {
            if (r == null || !r.mandatory) return null
            return ThinkingLevel.fromWire(r.defaultEffort) ?: nearest(ThinkingLevel.MEDIUM, r.supportedEfforts) ?: ThinkingLevel.MEDIUM
        }
        return nearest(level, r?.supportedEfforts) ?: level
    }

    /** Levels worth offering for this model: off unless thinking is mandatory, then what it accepts. */
    fun available(info: ModelInfo?): List<ThinkingLevel> {
        val r = info?.reasoning
        val words = r?.supportedEfforts
        val out = ArrayList<ThinkingLevel>(ThinkingLevel.entries.size)
        if (r == null || !r.mandatory) out.add(ThinkingLevel.OFF)
        if (words == null) {
            for (l in ThinkingLevel.entries) if (l != ThinkingLevel.OFF) out.add(l)
        } else {
            for (l in ThinkingLevel.entries) if (l != ThinkingLevel.OFF && l.wire in words) out.add(l)
            if (out.size == (if (r.mandatory) 0 else 1)) for (l in ThinkingLevel.entries) if (l != ThinkingLevel.OFF) out.add(l)
        }
        return out
    }

    /**
     * The `reasoning` request object, or null to send nothing. Off on a model that thinks
     * by default becomes `enabled:false`; off on a model that always thinks sends nothing,
     * because the router rejects a refusal it cannot honour.
     */
    fun wire(level: ThinkingLevel, info: ModelInfo?): ReasoningWire? {
        if (info != null && !info.supportsReasoning) return null
        val r = info?.reasoning
        if (level == ThinkingLevel.OFF) {
            if (r == null || r.mandatory || !r.defaultEnabled) return null
            return ReasoningWire(effort = null, enabled = false)
        }
        val chosen = nearest(level, r?.supportedEfforts) ?: level
        return ReasoningWire(effort = chosen.wire, enabled = null)
    }

    /** The supported level closest in rank to [level]; ties go to the lighter one. */
    private fun nearest(level: ThinkingLevel, words: List<String>?): ThinkingLevel? {
        if (words == null) return null
        var best: ThinkingLevel? = null
        var bestDistance = Int.MAX_VALUE
        for (l in ThinkingLevel.entries) {
            if (l == ThinkingLevel.OFF || l.wire !in words) continue
            val d = kotlin.math.abs(l.ordinal - level.ordinal)
            if (d < bestDistance) {
                bestDistance = d
                best = l
            }
        }
        return best
    }
}

class ReasoningWire(val effort: String?, val enabled: Boolean?)
