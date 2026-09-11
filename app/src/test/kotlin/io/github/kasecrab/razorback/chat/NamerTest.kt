package io.github.kasecrab.razorback.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NamerTest {

    @Test
    fun stripsTheWrappingModelsAdd() {
        assertEquals("Moon Facts", Namer.clean("\"Moon Facts.\""))
        assertEquals("Moon Facts", Namer.clean("**Moon Facts**"))
        assertEquals("Moon Facts", Namer.clean("Title: Moon Facts\nSomething else"))
        assertEquals("Moon Facts", Namer.clean("\n\n  Moon   Facts  \n"))
        assertEquals("Fakten zum Mond", Namer.clean("„Fakten zum Mond“".replace('„', '"').replace('“', '"')))
    }

    @Test
    fun keepsTitlesShortAndRefusesEmptyOnes() {
        val long = "A Very Long Title That Goes On And On About Everything Under The Sun"
        val cleaned = Namer.clean(long)!!
        assertEquals(Namer.MAX_TITLE, cleaned.length)
        assertEquals('…', cleaned.last())
        assertNull(Namer.clean("\"\""))
        assertNull(Namer.clean("   \n  "))
    }
}
