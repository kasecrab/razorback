package io.github.kasecrab.razorback.remote

import io.github.kasecrab.razorback.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayUrlTest {
    @Test
    fun httpsAndLoopbackAreRelaysAndNothingElseIs() {
        assertTrue(RelayUrl.acceptable("https://ah-relay.me.workers.dev/"))
        // The two below are allowed because this runs under a debug build, which
        // is where a relay on the phone's own host belongs; see the next test.
        assertTrue(BuildConfig.DEBUG)
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
    fun aPlainLoopbackRelayIsATestingThingAndNotAShippedOne() {
        // Nothing here can turn BuildConfig.DEBUG off, so what is pinned is the
        // shape of the rule: the http branch is the debug flag and the loopback
        // hosts together, never the hosts on their own. Shipped, an app already
        // on the phone could otherwise be the relay by listening on a port.
        assertTrue(RelayUrl.acceptable("http://127.0.0.1:8787") == BuildConfig.DEBUG)
        assertTrue(RelayUrl.acceptable("http://[::1]:8787") == BuildConfig.DEBUG)
        // https is the rule either way, and everything else is refused either way.
        assertTrue(RelayUrl.acceptable("https://127.0.0.1:8787"))
        assertFalse(RelayUrl.acceptable("http://10.0.2.2:8787"))
    }

    @Test
    fun theSocketFormKeepsTheHostAndPathAndDropsTheSlash() {
        assertEquals("wss://ah-relay.me.workers.dev", RelayUrl.socket("https://ah-relay.me.workers.dev/"))
        assertEquals("ws://127.0.0.1:8787", RelayUrl.socket("http://127.0.0.1:8787"))
        assertEquals("ah-relay.me.workers.dev", RelayUrl.host("https://ah-relay.me.workers.dev/"))
    }
}
