package io.github.kasecrab.razorback.ui.models

/**
 * Subsequence match with a small preference for starts of words and contiguous runs.
 * Every space-separated term must match; the score orders the results.
 */
object Fuzzy {
    fun score(query: String, text: String): Int {
        if (query.isEmpty()) return 0
        var total = 0
        for (term in query.split(' ')) {
            if (term.isEmpty()) continue
            val s = scoreTerm(term, text)
            if (s < 0) return -1
            total += s
        }
        return total
    }

    private fun scoreTerm(term: String, text: String): Int {
        var ti = 0
        var score = 0
        var last = -2
        for (ch in term) {
            val lower = ch.lowercaseChar()
            var found = -1
            var k = ti
            while (k < text.length) {
                if (text[k].lowercaseChar() == lower) {
                    found = k
                    break
                }
                k++
            }
            if (found < 0) return -1
            score += 10
            if (found == 0) score += 30 else if (!text[found - 1].isLetterOrDigit()) score += 20
            if (found == last + 1) score += 15
            last = found
            ti = found + 1
        }
        return score - minOf(text.length, 200) / 4
    }
}
