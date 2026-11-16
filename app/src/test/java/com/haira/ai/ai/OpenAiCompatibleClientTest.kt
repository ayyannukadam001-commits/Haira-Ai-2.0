package com.haira.ai.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OpenAiCompatibleClientTest {
    @Test
    fun buildsOpenAiCompatibleChatEndpoints() {
        assertEquals(
            "https://example.com/v1/chat/completions",
            OpenAiCompatibleClient.endpointFor("https://example.com")
        )
        assertEquals(
            "https://example.com/v1/chat/completions",
            OpenAiCompatibleClient.endpointFor("https://example.com/v1/")
        )
        assertEquals(
            "https://gateway.example/api/v1/chat/completions",
            OpenAiCompatibleClient.endpointFor("https://gateway.example/api/v1")
        )
        assertEquals(
            "https://example.com/v1/chat/completions",
            OpenAiCompatibleClient.endpointFor("https://example.com/v1/chat/completions")
        )
    }

    @Test
    fun rejectsInsecureOrAmbiguousProviderUrls() {
        listOf(
            "http://example.com",
            "https://user@example.com",
            "https://example.com?token=secret",
            "https://example.com#fragment",
            "not a URL"
        ).forEach { input ->
            assertThrows(IllegalArgumentException::class.java) {
                OpenAiCompatibleClient.endpointFor(input)
            }
        }
    }
}
