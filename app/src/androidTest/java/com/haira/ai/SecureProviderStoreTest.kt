package com.haira.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.haira.ai.ai.RemoteProviderConfig
import com.haira.ai.data.SecureProviderStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureProviderStoreTest {
    @Test
    fun providerCredentialsRoundTripEncryptedAndCanBeCleared() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SecureProviderStore(context)
        val preferences = context.getSharedPreferences("haira_secure_provider", 0)
        val config = RemoteProviderConfig(
            baseUrl = "https://gateway.example/v1",
            model = "auto",
            apiKey = "test-secret-key-do-not-store-plaintext",
            enabled = true
        )

        try {
            store.clear()
            store.save(config)
            assertEquals(config, store.load())
            val storedValue = preferences.getString("encrypted_provider_config", "").orEmpty()
            assertFalse(storedValue.contains(config.apiKey))
            assertFalse(storedValue.contains(config.baseUrl))
        } finally {
            store.clear()
        }
        assertEquals(null, store.load())
    }
}
