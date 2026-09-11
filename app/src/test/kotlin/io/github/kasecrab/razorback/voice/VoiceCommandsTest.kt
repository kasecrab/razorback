package io.github.kasecrab.razorback.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandsTest {
    @Test
    fun plainRequestsToCloseAreCommands() {
        for (s in listOf("Close voice mode.", "exit the voice mode please", "Okay, end voice chat", "close this screen", "Stop voice mode", "turn voice off", "Quit the voice conversation"))
            assertTrue(s, VoiceCommands.closes(s))
    }

    @Test
    fun ordinaryTalkAboutVoiceOrScreensIsNot() {
        for (s in listOf("stop", "What is voice mode?", "Can you close the window in the story?", "Tell me how to end a voice chat gracefully in my Android app when the user leaves", "the screen is dark"))
            assertFalse(s, VoiceCommands.closes(s))
    }
}
