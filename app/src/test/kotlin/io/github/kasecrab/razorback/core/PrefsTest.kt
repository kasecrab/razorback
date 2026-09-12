package io.github.kasecrab.razorback.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a settings file is allowed to carry.
 *
 * The export offers to leave the keys out, and used to take everything that was
 * not itself a key — which included eight bytes of SHA-256 of each of them.
 */
class PrefsTest {

    @Test
    fun theAppsOwnSettingsGoOut() {
        assertTrue(Prefs.exportable("model.id"))
        assertTrue(Prefs.exportable("prompt.system"))
        assertTrue(Prefs.exportable("theme.accent"))
        assertTrue(Prefs.exportable("tools.web_search"))
        assertTrue(Prefs.exportable("ui.haptics"))
        assertTrue(Prefs.exportable("voice.tts_voice"))
        assertTrue(Prefs.exportable("relay.url"))
    }

    @Test
    fun nothingThatSaysAnythingAboutAKeyDoes() {
        // A fingerprint reads nothing back, but it answers "is this the key?"
        // for anybody who already holds a guess at one.
        assertFalse(Prefs.exportable("verified.openrouter"))
        assertFalse(Prefs.exportable("secret.openrouter"))
        assertFalse(Prefs.exportable("keys.lost_keystore"))
        assertFalse(Prefs.exportable("dev.base_url"))
        // And nothing simply because nobody thought about it.
        assertFalse(Prefs.exportable("whatever.comes.next"))
    }
}
