package com.haira.ai.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RepositorySyncServiceTest {
    private val service = RepositorySyncService()

    @Test
    fun acceptsCanonicalPublicGitHubRepositoryUrls() {
        assertEquals(
            "octocat" to "Hello-World",
            service.parseGitHubUrl("https://github.com/octocat/Hello-World")
        )
        assertEquals(
            "owner" to "repo",
            service.parseGitHubUrl("https://github.com/owner/repo.git")
        )
        assertEquals(
            "owner" to "repo",
            service.parseGitHubUrl("https://github.com/owner/repo/")
        )
    }

    @Test
    fun rejectsNonGitHubHostsAndUnsafeUrls() {
        listOf(
            "https://github.com.evil.example/owner/repo",
            "http://github.com/owner/repo",
            "https://github.com:444/owner/repo",
            "https://user@github.com/owner/repo",
            "https://github.com/owner/repo/tree/main",
            "https://github.com/../repo",
            "https://github.com/owner/repo?tab=readme",
            "https://github.com/owner/repo#readme"
        ).forEach { input ->
            assertThrows(IllegalArgumentException::class.java) {
                service.parseGitHubUrl(input)
            }
        }
    }
}
