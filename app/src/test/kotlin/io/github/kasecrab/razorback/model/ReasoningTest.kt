package io.github.kasecrab.razorback.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReasoningTest {

    private fun model(r: ReasoningInfo?, supports: Boolean = true) =
        ModelInfo("m", "m", 0, 0.0, 0.0, 0.0, supports, true, setOf("text"), setOf("text"), r)

    @Test
    fun unknownModelKeepsTheChoice() {
        assertEquals(ThinkingLevel.HIGH, Reasoning.effective(ThinkingLevel.HIGH, null))
        assertNull(Reasoning.effective(ThinkingLevel.OFF, null))
        assertEquals(ThinkingLevel.entries, Reasoning.available(null))
    }

    @Test
    fun mandatoryThinkingFallsBackToTheDefaultEffort() {
        val m = model(ReasoningInfo(true, true, listOf("high", "medium", "low"), "medium"))
        assertEquals(ThinkingLevel.MEDIUM, Reasoning.effective(ThinkingLevel.OFF, m))
        assertEquals(listOf(ThinkingLevel.LOW, ThinkingLevel.MEDIUM, ThinkingLevel.HIGH), Reasoning.available(m))
        assertNull(Reasoning.wire(ThinkingLevel.OFF, m))
    }

    @Test
    fun offOnAModelThatThinksByDefaultIsAnExplicitRefusal() {
        val m = model(ReasoningInfo(false, true, listOf("max", "high", "low"), "high"))
        val w = Reasoning.wire(ThinkingLevel.OFF, m)!!
        assertEquals(false, w.enabled)
        assertNull(w.effort)
        assertEquals(listOf(ThinkingLevel.OFF, ThinkingLevel.LOW, ThinkingLevel.HIGH, ThinkingLevel.MAX), Reasoning.available(m))
    }

    @Test
    fun offOnAModelThatIsQuietByDefaultSendsNothing() {
        val m = model(ReasoningInfo(false, false, null, null))
        assertNull(Reasoning.wire(ThinkingLevel.OFF, m))
        assertEquals("high", Reasoning.wire(ThinkingLevel.HIGH, m)!!.effort)
    }

    @Test
    fun nearestEffortTiesGoLighter() {
        val m = model(ReasoningInfo(false, true, listOf("max", "high", "low"), "high"))
        assertEquals(ThinkingLevel.LOW, Reasoning.effective(ThinkingLevel.MEDIUM, m))
        assertEquals(ThinkingLevel.LOW, Reasoning.effective(ThinkingLevel.MINIMAL, m))
        assertEquals(ThinkingLevel.HIGH, Reasoning.effective(ThinkingLevel.XHIGH, m))
        assertEquals(ThinkingLevel.MAX, Reasoning.effective(ThinkingLevel.MAX, m))
    }

    @Test
    fun modelWithoutReasoningNeverThinks() {
        val m = model(null, supports = false)
        assertNull(Reasoning.effective(ThinkingLevel.HIGH, m))
        assertNull(Reasoning.wire(ThinkingLevel.HIGH, m))
    }
}
