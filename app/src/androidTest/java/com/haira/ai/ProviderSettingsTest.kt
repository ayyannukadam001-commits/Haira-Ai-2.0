package com.haira.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.haira.ai.data.ProviderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderSettingsTest {
    @Test
    fun apiKeyIsEncryptedAndCanBeReadBack() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = ProviderSettings(context)
        val rawPreferences = context.getSharedPreferences("haira_provider_settings", Context.MODE_PRIVATE)
        try {
            settings.save(
                baseUrl = "https://gateway.example/v1",
                model = "auto",
                apiKey = "test-secret-key",
                enabled = true
            )

            assertEquals("https://gateway.example/v1", settings.baseUrl)
            assertEquals("auto", settings.model)
            assertEquals(true, settings.isEnabled)
            assertEquals("test-secret-key", settings.apiKey)
            assertNotEquals("test-secret-key", rawPreferences.getString("api_key", null))
        } finally {
            settings.clear()
        }
    }

    @Test
    fun rejectsNonHttpsProviderEndpoints() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = ProviderSettings(context)
        try {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                settings.save("http://localhost:3001/v1", "auto", "key", true)
            }
        } finally {
            settings.clear()
        }
    }
}
