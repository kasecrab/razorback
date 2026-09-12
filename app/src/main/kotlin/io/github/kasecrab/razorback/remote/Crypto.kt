package io.github.kasecrab.razorback.remote

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The key ladder, and the seal every frame goes through.
 *
 * One pairing code is the only secret. Everything else — the name of the hub,
 * the key the relay is given, the key each direction is sealed under — is
 * derived from it, so pairing moves exactly one thing between two devices.
 *
 * The salts and info strings are the harness's, character for character. A
 * difference in any of them is a link that connects and then cannot read a
 * word, which is a miserable thing to debug; [CryptoTest] checks them against
 * values the harness produced.
 */
object Crypto {

    private const val SALT = "ah-remote v1"
    private const val LINK_SALT = "ah-remote link v1"
    private const val INFO_HUB = "hub"
    private const val INFO_RELAY = "relay"
    private const val INFO_D2P = "d2p"
    private const val INFO_P2D = "p2d"

    const val LINK_BYTES = 16

    /** Which way a frame is travelling, bound into what is sealed. */
    enum class Dir(val byte: Byte) { D2P(0), P2D(1) }

    /** Everything the pairing code leads to. */
    class Keys(code: ByteArray) {
        private val prk = extract(SALT.toByteArray(), code)

        /** Where the two peers meet: 32 lowercase hex characters. */
        val hub: String = hex(expand(prk, listOf(INFO_HUB.toByteArray()), 16))

        /** The only key the relay is given. */
        val relayKey: ByteArray = expand(prk, listOf(INFO_RELAY.toByteArray()), 32)

        private val d2p = expand(prk, listOf(INFO_D2P.toByteArray()), 32)
        private val p2d = expand(prk, listOf(INFO_P2D.toByteArray()), 32)

        /**
         * The key one connection seals under.
         *
         * The desktop's binds only its own link, because every attached phone
         * has to open it. A phone's binds both, so two phones — and the same
         * phone twice — never share a key.
         */
        fun linkKey(dir: Dir, link: ByteArray, plink: ByteArray): ByteArray {
            val (base, info) = when (dir) {
                Dir.D2P -> d2p to listOf(INFO_D2P.toByteArray(), link)
                Dir.P2D -> p2d to listOf(INFO_P2D.toByteArray(), link, plink)
            }
            return expand(extract(LINK_SALT.toByteArray(), base), info, 32)
        }

        /** The signature that gets a socket open. */
        fun signConnect(role: String, ts: Long, nonce: String): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(relayKey, "HmacSHA256"))
            return b64u(mac.doFinal(connectMessage(hub, role, ts, nonce).toByteArray()))
        }
    }

    /** The message a connect signature covers, built here and nowhere else. */
    fun connectMessage(hub: String, role: String, ts: Long, nonce: String): String =
        "ah/v1 connect|$hub|$role|$ts|$nonce"

    /**
     * Seals outgoing frames, in order, numbering them as it goes.
     *
     * The sequence number is the nonce, so it is never used twice under one
     * key and never has to travel separately — it is already in the envelope.
     */
    class Sealer(key: ByteArray, private val dir: Dir,
                 private val link: ByteArray, private val plink: ByteArray) {
        private val key = SecretKeySpec(key, "AES")
        private var seq = 0L

        /** Seal one payload, returning the number it went out under. */
        fun seal(plain: ByteArray): Pair<Long, String> {
            seq += 1
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce(seq)))
            cipher.updateAAD(aad(link, if (dir == Dir.D2P) null else plink, dir, seq))
            return seq to b64u(cipher.doFinal(plain))
        }
    }

    /** Why a frame did not open. */
    enum class Refusal { REPLAY, ENCODING, REFUSED }

    class Opened(val plain: ByteArray?, val why: Refusal?)

    /** Opens incoming frames, and refuses to open one twice. */
    class Opener(key: ByteArray, private val dir: Dir,
                 private val link: ByteArray, private val plink: ByteArray) {
        private val key = SecretKeySpec(key, "AES")
        private var lastSeq = 0L

        /** Start from a number already reached, for a stream picked back up. */
        fun resumeFrom(seq: Long) {
            lastSeq = seq
        }

        /** The last number opened. */
        val seq: Long get() = lastSeq

        fun open(seq: Long, ct: String): Opened {
            // Before the cipher, not after: the cheap check turns a flood of
            // replayed frames into a flood of integer comparisons.
            if (seq <= lastSeq) return Opened(null, Refusal.REPLAY)
            val raw = try {
                unb64u(ct)
            } catch (_: IllegalArgumentException) {
                return Opened(null, Refusal.ENCODING)
            }
            return try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce(seq)))
                cipher.updateAAD(aad(link, if (dir == Dir.D2P) null else plink, dir, seq))
                val plain = cipher.doFinal(raw)
                // Only now. A frame that did not open must not move the
                // window, or anyone able to inject one numbered far ahead
                // could wedge the link shut for good.
                lastSeq = seq
                Opened(plain, null)
            } catch (_: GeneralSecurityException) {
                Opened(null, Refusal.REFUSED)
            }
        }
    }

    /**
     * The frame's number, as a nonce. The leading bytes are a reserved epoch:
     * the key is already per-connection, so a random prefix would add nothing
     * and a number recomputed from the envelope is one fewer thing that can
     * arrive wrong.
     */
    private fun nonce(seq: Long): ByteArray {
        val out = ByteArray(12)
        for (i in 0 until 8) out[4 + i] = ((seq shr ((7 - i) * 8)) and 0xFF).toByte()
        return out
    }

    /**
     * What every frame is sealed under: which link, which phone, which way,
     * and which number. A relay that reorders frames, or labels one as coming
     * from somewhere else, cannot make it open.
     */
    private fun aad(link: ByteArray, plink: ByteArray?, dir: Dir, seq: Long): ByteArray {
        val out = ByteArray(41)
        System.arraycopy(link, 0, out, 0, LINK_BYTES)
        if (plink != null) System.arraycopy(plink, 0, out, 16, LINK_BYTES)
        out[32] = dir.byte
        for (i in 0 until 8) out[33 + i] = ((seq shr ((7 - i) * 8)) and 0xFF).toByte()
        return out
    }

    private fun extract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        return mac.doFinal(ikm)
    }

    private fun expand(prk: ByteArray, info: List<ByteArray>, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val out = ByteArray(length)
        var block = ByteArray(0)
        var done = 0
        var counter = 1
        while (done < length) {
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(block)
            for (part in info) mac.update(part)
            mac.update(counter.toByte())
            block = mac.doFinal()
            val take = minOf(block.size, length - done)
            System.arraycopy(block, 0, out, done, take)
            done += take
            counter += 1
        }
        return out
    }

    /** Sixteen random bytes naming this connection. */
    fun newLink(): ByteArray = ByteArray(LINK_BYTES).also { SecureRandom().nextBytes(it) }

    /** Enough that two dials in one millisecond do not sign the same thing. */
    fun newNonce(): String = b64u(ByteArray(12).also { SecureRandom().nextBytes(it) })

    fun hex(raw: ByteArray): String =
        raw.joinToString("") { "%02x".format(it) }

    fun unhex(s: String): ByteArray? {
        if (s.length % 2 != 0) return null
        val out = ByteArray(s.length / 2)
        for (i in out.indices) {
            out[i] = s.substring(i * 2, i * 2 + 2).toIntOrNull(16)?.toByte() ?: return null
        }
        return out
    }

    /**
     * Base64url without padding, the way everything on this wire is written.
     * The platform's own, not Android's, so everything above can be tested
     * without a device.
     */
    fun b64u(raw: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(raw)

    fun unb64u(text: String): ByteArray = Base64.getUrlDecoder().decode(text)
}
