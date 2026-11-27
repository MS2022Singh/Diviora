package com.diviora.music.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Per-install media key for encrypted offline copies. A random 128-bit AES key is generated once and stored only
 * *wrapped* (AES-GCM) by a non-exportable key in the Android Keystore (hardware-backed on most phones). Copying the app's
 * files to another device is useless: the wrapping key never leaves this phone.
 */
class KeyVault(private val context: Context) {
    private val prefs by lazy { context.getSharedPreferences("diviora_vault", Context.MODE_PRIVATE) }

    @Volatile private var cached: ByteArray? = null

    @Synchronized
    fun mediaKey(): ByteArray {
        cached?.let { return it }
        val stored = prefs.getString(KEY_WRAPPED, null)
        val key = if (stored != null) unwrap(stored) else ByteArray(16).also { SecureRandom().nextBytes(it); prefs.edit().putString(KEY_WRAPPED, wrap(it)).apply() }
        cached = key
        return key
    }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun wrap(raw: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, keystoreKey()) }
        val out = c.iv + c.doFinal(raw)
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun unwrap(b64: String): ByteArray {
        val all = Base64.decode(b64, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, all, 0, 12)) }
        return c.doFinal(all, 12, all.size - 12)
    }

    companion object {
        private const val ALIAS = "diviora_media_wrap"
        private const val KEY_WRAPPED = "media_key"
    }
}
