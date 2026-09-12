package io.github.kasecrab.razorback.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayUrlTest {
    @Test
    fun httpsAndLoopbackAreRelaysAndNothingElseIs() {
        assertTrue(RelayUrl.acceptable("https://ah-relay.me.workers.dev/"))
        assertTrue(RelayUrl.acceptable("http://127.0.0.1:8787"))
        assertTrue(RelayUrl.acceptable("http://localhost:8787"))
        assertFalse(RelayUrl.acceptable("http://relay.example.com"))
        assertFalse(RelayUrl.acceptable("ws://relay.example.com"))
        assertFalse(RelayUrl.acceptable("https://"))
        assertFalse(RelayUrl.acceptable("relay.example.com"))
        assertFalse(RelayUrl.acceptable("https://relay.example.com/a b"))
        assertFalse(RelayUrl.acceptable(""))
    }

    @Test
    fun theSocketFormKeepsTheHostAndPathAndDropsTheSlash() {
        assertEquals("wss://ah-relay.me.workers.dev", RelayUrl.socket("https://ah-relay.me.workers.dev/"))
        assertEquals("ws://127.0.0.1:8787", RelayUrl.socket("http://127.0.0.1:8787"))
        assertEquals("ah-relay.me.workers.dev", RelayUrl.host("https://ah-relay.me.workers.dev/"))
    }
}
