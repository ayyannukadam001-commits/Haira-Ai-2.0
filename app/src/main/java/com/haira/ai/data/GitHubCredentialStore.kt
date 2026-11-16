package com.haira.ai.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores a user-supplied, least-privilege GitHub token encrypted with Android Keystore. */
class GitHubCredentialStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("haira_github_secure", Context.MODE_PRIVATE)

    @Synchronized
    fun save(token: String) {
        require(token.isNotBlank()) { "Enter a GitHub token." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(token.trim().toByteArray(Charsets.UTF_8))
        val payload = JSONObject()
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .toString()
        check(prefs.edit().putString(KEY, payload).commit()) { "Unable to save the encrypted GitHub token." }
    }

    @Synchronized
    fun load(): String? = runCatching {
        val payload = prefs.getString(KEY, null) ?: return null
        val wrapper = JSONObject(payload)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, Base64.decode(wrapper.getString("iv"), Base64.NO_WRAP))
        )
        String(cipher.doFinal(Base64.decode(wrapper.getString("data"), Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrElse {
        clear()
        null
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY).commit()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ALIAS = "haira_github_token_aes_gcm_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY = "encrypted_github_token"
    }
}
