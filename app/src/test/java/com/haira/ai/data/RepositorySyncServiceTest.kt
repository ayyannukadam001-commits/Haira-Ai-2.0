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
    }

    @Test
    fun rejectsNonGitHubHostsAndUnsafePaths() {
        assertThrows(IllegalArgumentException::class.java) {
            service.parseGitHubUrl("https://github.com.evil.example/owner/repo")
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.parseGitHubUrl("http://github.com/owner/repo")
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.parseGitHubUrl("https://github.com/owner/repo/tree/main")
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.parseGitHubUrl("https://github.com/../repo")
        }
    }
}
