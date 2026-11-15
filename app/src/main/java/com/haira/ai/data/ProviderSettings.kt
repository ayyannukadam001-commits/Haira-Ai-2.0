package com.haira.ai.data

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores endpoint metadata in app-private preferences and encrypts the API key
 * with an AES-GCM key that never leaves Android Keystore.
 */
class ProviderSettings(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("haira_provider_settings", Context.MODE_PRIVATE)
    private val keyStore = EncryptedValueStore(preferences)

    val baseUrl: String
        get() = preferences.getString("base_url", "").orEmpty()

    val model: String
        get() = preferences.getString("model", "auto").orEmpty().ifBlank { "auto" }

    val isEnabled: Boolean
        get() = preferences.getBoolean("enabled", false)

    val apiKey: String?
        get() = keyStore.read("api_key")

    fun save(baseUrl: String, model: String, apiKey: String, enabled: Boolean) {
        val normalized = baseUrl.trim().trimEnd('/')
        val parsed = Uri.parse(normalized)
        require(
            parsed.scheme.equals("https", ignoreCase = true) &&
                !parsed.host.isNullOrBlank() &&
                parsed.userInfo == null &&
                parsed.query == null &&
                parsed.fragment == null
        ) {
            "Use a valid HTTPS endpoint without embedded credentials, query strings or fragments."
        }
        require(normalized.length <= 500) { "Endpoint URL is too long." }
        require(model.isNotBlank() && model.length <= 160) { "Enter a valid model name." }
        require(apiKey.isNotBlank() && apiKey.length <= 2_000) { "Enter the provider API key." }
        preferences.edit()
            .putString("base_url", normalized)
            .putString("model", model.trim())
            .putBoolean("enabled", enabled)
            .apply()
        keyStore.write("api_key", apiKey.trim())
    }

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("enabled", enabled).apply()
    }

    fun clear() {
        preferences.edit().remove("base_url").remove("model").remove("enabled").apply()
        keyStore.remove("api_key")
    }

    private class EncryptedValueStore(private val preferences: android.content.SharedPreferences) {
        private val alias = "haira.provider.aesgcm.v1"

        @Synchronized
        fun write(name: String, value: String) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val combined = cipher.iv + encrypted
            preferences.edit().putString(name, Base64.encodeToString(combined, Base64.NO_WRAP)).apply()
        }

        @Synchronized
        fun read(name: String): String? {
            val encoded = preferences.getString(name, null) ?: return null
            return runCatching {
                val combined = Base64.decode(encoded, Base64.NO_WRAP)
                require(combined.size > 12) { "Encrypted provider key is invalid." }
                val iv = combined.copyOfRange(0, 12)
                val encrypted = combined.copyOfRange(12, combined.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
                String(cipher.doFinal(encrypted), Charsets.UTF_8)
            }.getOrNull()
        }

        @Synchronized
        fun remove(name: String) {
            preferences.edit().remove(name).apply()
        }

        private fun getOrCreateKey(): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val existing = store.getKey(alias, null)
            if (existing is SecretKey) return existing
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            return generator.generateKey()
        }
    }
}
