package io.github.kasecrab.razorback.remote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The ladder, against values the harness produced.
 *
 * Every constant below came out of
 * `cargo test -p ah-remote --lib vectors::dump_for_the_phone -- --ignored`,
 * from a pairing code of twenty nines. If the two implementations ever drift,
 * this is where it shows — before anyone pairs a real phone and finds a link
 * that connects and then cannot read a word.
 */
class CryptoTest {

    private val code = ByteArray(20) { 9 }
    private val link = ByteArray(16) { 0x11 }
    private val plink = ByteArray(16) { 0x22 }

    @Test
    fun theCodeIsShownThoseSameCharacters() {
        assertEquals("BEEQ-SCIJ-BEEQ-SCIJ-BEEQ-SCIJ-BEEQ-SCIJ", Codes.format(code))
        assertArrayEquals(code, Codes.parse("BEEQ-SCIJ-BEEQ-SCIJ-BEEQ-SCIJ-BEEQ-SCIJ"))
    }

    @Test
    fun theHubIsTheOneTheHarnessDerives() {
        assertEquals("f21977acfc37cd3c344c70ac9b53221e", Crypto.Keys(code).hub)
    }

    @Test
    fun theRelayIsGivenTheKeyTheHarnessGivesIt() {
        assertEquals(
            "m_kldmGUEMIByUro3bQ2iEv0HRX7UGTOIJzKDMfg8J0",
            Crypto.b64u(Crypto.Keys(code).relayKey),
        )
    }

    @Test
    fun bothDirectionsAgreeOnTheirKeys() {
        val keys = Crypto.Keys(code)
        assertEquals(
            "h4vYzvlp35bMJx7ooE8_Bjuw2dhSMuJCOe-5TzaPQcU",
            Crypto.b64u(keys.linkKey(Crypto.Dir.D2P, link, plink)),
        )
        assertEquals(
            "Ey-W1nBAbJwtfnkqE6DuSwobHOL490jrFJ3EWNkHcC4",
            Crypto.b64u(keys.linkKey(Crypto.Dir.P2D, link, plink)),
        )
    }

    @Test
    fun aFrameTheHarnessSealedOpensHere() {
        val keys = Crypto.Keys(code)
        val opener = Crypto.Opener(
            keys.linkKey(Crypto.Dir.D2P, link, plink), Crypto.Dir.D2P, link, ByteArray(16),
        )
        val opened = opener.open(
            1,
            "WyE_s2H9a6HdhCqE361oZWLuO6KFavAsl1DAu89CC5f4JF342bjQ0NlmP5w3F26FyzG_O7nL7L0",
        )
        assertNull(opened.why)
        assertEquals(
            """{"k":"notice","text":"the kettle is on"}""",
            String(opened.plain!!, Charsets.UTF_8),
        )
    }

    @Test
    fun aFrameSealedHereIsTheOneTheHarnessMade() {
        val keys = Crypto.Keys(code)
        val sealer = Crypto.Sealer(
            keys.linkKey(Crypto.Dir.P2D, link, plink), Crypto.Dir.P2D, link, plink,
        )
        val (seq, ct) = sealer.seal("""{"k":"list"}""".toByteArray())
        assertEquals(1L, seq)
        // AES-GCM with a fixed key and nonce is deterministic, so this is the
        // same ciphertext the harness produced, byte for byte.
        assertEquals("YXdOxMlHioo89WUSbRfyjSlZukCEg2kgEhLrwA", ct)
    }

    @Test
    fun aSignatureIsTheOneTheRelayWillAccept() {
        // The same moment and nonce the harness signed.
        val keys = Crypto.Keys(ByteArray(20) { 42 })
        assertEquals(
            "HWIjvcOJvo1tcMfcPpr650a9iq7emdP_lJoDaroOOMk",
            keys.signConnect("phone", 1_757_000_000_000, "AAAAAAAAAAAAAAAA"),
        )
    }

    @Test
    fun aFrameReplayedWithItsOwnNumberIsRefused() {
        val keys = Crypto.Keys(code)
        val key = keys.linkKey(Crypto.Dir.D2P, link, plink)
        val sealer = Crypto.Sealer(key, Crypto.Dir.D2P, link, ByteArray(16))
        val opener = Crypto.Opener(key, Crypto.Dir.D2P, link, ByteArray(16))
        val (seq, ct) = sealer.seal("once".toByteArray())
        assertNotNull(opener.open(seq, ct).plain)
        assertEquals(Crypto.Refusal.REPLAY, opener.open(seq, ct).why)
    }

    @Test
    fun aFrameThatDidNotOpenDoesNotMoveTheWindow() {
        val keys = Crypto.Keys(code)
        val key = keys.linkKey(Crypto.Dir.D2P, link, plink)
        val sealer = Crypto.Sealer(key, Crypto.Dir.D2P, link, ByteArray(16))
        val opener = Crypto.Opener(key, Crypto.Dir.D2P, link, ByteArray(16))
        val (seq, ct) = sealer.seal("carry on".toByteArray())
        // Somebody injects nonsense numbered far ahead.
        assertEquals(Crypto.Refusal.REFUSED, opener.open(Long.MAX_VALUE, ct).why)
        // The real frame still arrives and is still read.
        assertNotNull(opener.open(seq, ct).plain)
    }

    @Test
    fun aFrameFromAnotherLinkDoesNotOpen() {
        val keys = Crypto.Keys(code)
        val theirs = ByteArray(16) { 0x99.toByte() }
        val sealer = Crypto.Sealer(
            keys.linkKey(Crypto.Dir.D2P, theirs, plink), Crypto.Dir.D2P, theirs, ByteArray(16),
        )
        val opener = Crypto.Opener(
            keys.linkKey(Crypto.Dir.D2P, link, plink), Crypto.Dir.D2P, link, ByteArray(16),
        )
        val (seq, ct) = sealer.seal("from a link you retired".toByteArray())
        assertEquals(Crypto.Refusal.REFUSED, opener.open(seq, ct).why)
    }

    @Test
    fun aZeroTypedForAnOStillPairs() {
        val shown = Codes.format(code)
        assertArrayEquals(code, Codes.parse(shown.replace('O', '0').replace('I', '1')))
        assertArrayEquals(code, Codes.parse(shown.lowercase()))
        assertArrayEquals(code, Codes.parse(shown.replace("-", "")))
    }

    @Test
    fun somethingThatIsNotACodeIsNotReadAsOne() {
        assertNull(Codes.parse(""))
        assertNull(Codes.parse("AAAA"))
        assertNull(Codes.parse("hello, world!"))
        assertNull(Codes.parse("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA8"))
    }
}
