package com.haira.ai.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.haira.ai.ai.RemoteProviderConfig
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores provider URL/model/key as one authenticated AES-GCM encrypted blob.
 * The AES key is generated and retained by Android Keystore; the API key is
 * never written to preferences or logs in plaintext.
 */
class SecureProviderStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "haira_secure_provider",
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun save(config: RemoteProviderConfig) {
        val plain = JSONObject()
            .put("baseUrl", config.baseUrl.trim())
            .put("model", config.model.trim())
            .put("apiKey", config.apiKey)
            .put("enabled", config.enabled)
            .toString()
            .toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plain)
        val payload = JSONObject()
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .toString()
        check(preferences.edit().putString(CONFIG_KEY, payload).commit()) {
            "Unable to save encrypted provider settings."
        }
    }

    @Synchronized
    fun load(): RemoteProviderConfig? {
        val payload = preferences.getString(CONFIG_KEY, null) ?: return null
        return runCatching {
            val wrapper = JSONObject(payload)
            val iv = Base64.decode(wrapper.getString("iv"), Base64.NO_WRAP)
            val encrypted = Base64.decode(wrapper.getString("data"), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val plain = JSONObject(String(cipher.doFinal(encrypted), Charsets.UTF_8))
            RemoteProviderConfig(
                baseUrl = plain.getString("baseUrl"),
                model = plain.getString("model"),
                apiKey = plain.getString("apiKey"),
                enabled = plain.optBoolean("enabled", false)
            )
        }.getOrElse {
            clear()
            null
        }
    }

    @Synchronized
    fun clear() {
        preferences.edit().remove(CONFIG_KEY).commit()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "haira_provider_config_aes_gcm_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val CONFIG_KEY = "encrypted_provider_config"
    }
}
