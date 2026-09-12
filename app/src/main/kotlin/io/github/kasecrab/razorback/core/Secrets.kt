package io.github.kasecrab.razorback.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API keys at rest: AES-256-GCM under a non-exportable Android Keystore key. If the
 * keystore key is ever lost or invalidated every secret is wiped and the caller sees null,
 * so the UI can ask for the keys again instead of failing every request with garbage.
 */
class Secrets(private val prefs: Prefs) {

    private val cache = HashMap<String, String?>(4)

    @Synchronized
    fun get(name: String): String? {
        if (cache.containsKey(name)) return cache[name]
        val stored = prefs.rawString(PREFIX + name)
        val value = if (stored == null) null else decrypt(name, stored)
        cache[name] = value
        return value
    }

    @Synchronized
    fun put(name: String, value: String?) {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() }
        cache[name] = trimmed
        prefs.putRawString(PREFIX + name, if (trimmed == null) null else encrypt(trimmed))
    }

    fun has(name: String): Boolean = get(name) != null

    /**
     * One entry that will not open is dropped on its own: a stray or tampered value must
     * not take the other keys with it. Only the keystore key itself being gone, which
     * makes every entry unreadable at once, clears the store.
     */
    private fun decrypt(name: String, stored: String): String? = try {
        val blob = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES))
        String(cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES), Charsets.UTF_8)
    } catch (e: android.security.keystore.KeyPermanentlyInvalidatedException) {
        wipe(e)
        null
    } catch (e: java.security.UnrecoverableKeyException) {
        wipe(e)
        null
    } catch (e: GeneralSecurityException) {
        drop(name, e)
        null
    } catch (e: IllegalArgumentException) {
        drop(name, e)
        null
    }

    private fun drop(name: String, cause: Exception) {
        Log.w("stored secret $name unreadable, dropping it", cause)
        prefs.putRawString(PREFIX + name, null)
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val blob = ByteArray(iv.size + ct.size)
        System.arraycopy(iv, 0, blob, 0, iv.size)
        System.arraycopy(ct, 0, blob, iv.size, ct.size)
        return Base64.encodeToString(blob, Base64.NO_WRAP)
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(STORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun wipe(cause: Exception) {
        Log.w("stored secrets unreadable, clearing them", cause)
        cache.clear()
        prefs.removeAll(PREFIX)
    }

    companion object {
        const val PREFIX = "secret."
        const val OPENROUTER = "openrouter"
        const val DEEPGRAM = "deepgram"
        const val BRAVE = "brave"
        /** The pairing code for a machine, as typed or scanned. */
        const val RELAY = "relay"
        const val EXA = "exa"

        private const val STORE = "AndroidKeyStore"
        private const val ALIAS = "razorback_master"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}
