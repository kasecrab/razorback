package io.github.kasecrab.razorback.provider.openrouter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a debug build will let itself be pointed.
 *
 * The address comes in on an intent extra, which needs no permission and no
 * adb: any app on the phone can send one, and every request afterwards carries
 * the OpenRouter key in a header to wherever it points.
 */
class OpenRouterTest {

    @Test
    fun aMockServerIsOnThisPhoneOrTheEmulatorsHost() {
        assertTrue(OpenRouter.mockServer("http://10.0.2.2:8787"))
        assertTrue(OpenRouter.mockServer("http://127.0.0.1:8787/v1"))
        assertTrue(OpenRouter.mockServer("http://localhost:8787"))
        assertTrue(OpenRouter.mockServer("https://localhost:8787"))
    }

    @Test
    fun anythingElseIsSomebodyElsesServer() {
        assertFalse(OpenRouter.mockServer("https://attacker.example"))
        assertFalse(OpenRouter.mockServer("https://localhost.attacker.example"))
        assertFalse(OpenRouter.mockServer("https://openrouter.ai.attacker.example/api/v1"))
        assertFalse(OpenRouter.mockServer("not a url at all"))
        assertFalse(OpenRouter.mockServer(""))
    }
}
