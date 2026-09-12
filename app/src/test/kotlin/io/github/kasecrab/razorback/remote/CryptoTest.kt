package io.github.kasecrab.razorback.remote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
    fun aSecondStreamToTheSameMachineDoesNotSpendTheSameNumberAgain() {
        // A socket that dropped, or a machine link named a second time, means
        // a fresh stream of commands whose count starts at one again. That is
        // only safe if the key underneath is a new one, so the phone link the
        // key binds is drawn with the stream rather than kept across them.
        val keys = Crypto.Keys(code)
        val first = keys.outgoing(link)
        val second = keys.outgoing(link)
        assertFalse(first.plink.contentEquals(second.plink))
        val (firstSeq, firstCt) = first.seal("""{"k":"list"}""".toByteArray())
        val (secondSeq, secondCt) = second.seal("""{"k":"list"}""".toByteArray())
        assertEquals(1L, firstSeq)
        assertEquals(1L, secondSeq)
        // The same words under the same number, twice. Under one key those
        // would be the same bytes, and the keystream would be there for the
        // reading; under two keys they are not.
        assertNotEquals(firstCt, secondCt)
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
    fun aLinkIsThirtyTwoLowercaseHexCharactersOrItIsNotALink() {
        val written = "0f1e2d3c4b5a69788796a5b4c3d2e1f0"
        val bytes = Crypto.unlink(written)!!
        assertEquals(16, bytes.size)
        assertEquals(written, Crypto.hex(bytes))
        // Each of these used to come back as a link of whatever length it felt
        // like, and the first fixed-width copy made of one ended the process.
        assertNull(Crypto.unlink(""))
        assertNull(Crypto.unlink("ab"))
        assertNull(Crypto.unlink(written.dropLast(2)))
        assertNull(Crypto.unlink(written + "11"))
        assertNull(Crypto.unlink(written.uppercase()))
        assertNull(Crypto.unlink(written.dropLast(1) + "g"))
        assertNull(Crypto.unlink(written.dropLast(1) + " "))
    }

    @Test
    fun aSealIsNotMadeOverSomethingThatIsNotALink() {
        val keys = Crypto.Keys(code)
        val key = keys.linkKey(Crypto.Dir.D2P, link, plink)
        assertThrows(IllegalArgumentException::class.java) {
            Crypto.Opener(key, Crypto.Dir.D2P, ByteArray(1), ByteArray(16))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Crypto.Sealer(key, Crypto.Dir.P2D, link, ByteArray(0))
        }
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
