package io.github.kasecrab.razorback.ui.models

import io.github.kasecrab.razorback.model.ModelInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class RankingTest {

    private fun model(id: String, prompt: Double, completion: Double, intelligence: Double? = null, created: Long = 0L) =
        ModelInfo(id, id, 0, prompt, completion, 0.0, true, true, setOf("text"), setOf("text"), created = created, intelligence = intelligence)

    private val models = listOf(
        model("a/big", 10.0, 50.0, 50.0, created = 5),
        model("a/big:batch", 5.0, 25.0, 50.0, created = 5),
        model("b/mid", 1.0, 3.0, 40.0, created = 9),
        model("c/cheap", 0.1, 0.3, 35.0, created = 1),
        model("d/tiny", 0.05, 0.1, 10.0, created = 7),
        model("e/free:free", 0.0, 0.0, 20.0, created = 3),
        model("f/free-unscored:free", 0.0, 0.0, created = 2),
        model("g/unscored", 2.0, 2.0, created = 8),
    )

    @Test
    fun batchVariantsNeverShowAndRankedListsSkipVariants() {
        assertEquals(false, Ranking.all(models).any { it.isBatch })
        assertEquals(listOf("a/big", "b/mid", "c/cheap", "d/tiny"), Ranking.smartest(models).map { it.id })
    }

    @Test
    fun valueRanksIntelligencePerDollarAmongTheSmarterHalf() {
        // Scored paid: big 50, mid 40, cheap 35, tiny 10; the median floor is 35, so tiny is out.
        assertEquals(listOf("c/cheap", "b/mid", "a/big"), Ranking.value(models).map { it.id })
    }

    @Test
    fun freeListsScoredFirstThenByName() {
        assertEquals(listOf("e/free:free", "f/free-unscored:free"), Ranking.free(models).map { it.id })
    }

    @Test
    fun fastestOrdersMeasuredModelsAndCandidatesAreTheScoredAndTheMostUsed() {
        val speeds = mapOf("d/tiny" to 300.0, "a/big" to 40.0, "a/big:batch" to 900.0, "g/unscored" to 120.0)
        assertEquals(listOf("d/tiny", "g/unscored", "a/big"), Ranking.fastest(models, speeds).map { it.id })
        assertEquals(
            listOf("g/unscored", "a/big", "b/mid", "c/cheap", "d/tiny"),
            Ranking.speedCandidates(models, listOf("g/unscored", "e/free:free", "a/big")),
        )
    }

    @Test
    fun newestFirstAndPopularKeepsTheRoutersOrder() {
        assertEquals("b/mid", Ranking.newest(models).first().id)
        assertEquals(listOf("d/tiny", "a/big"), Ranking.popular(models, listOf("d/tiny", "x/missing", "a/big")).map { it.id })
    }
}
