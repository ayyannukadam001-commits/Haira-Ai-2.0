package com.haira.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.haira.ai.data.RepositorySyncService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositorySyncServiceTest {
    private val service = RepositorySyncService()

    @Test
    fun parsesStandardGitHubRepositoryUrls() {
        assertEquals(
            "ayyannukadam001-commits" to "freellmapi",
            service.parseGitHubUrl("https://github.com/ayyannukadam001-commits/freellmapi")
        )
        assertEquals(
            "ayyannukadam001-commits" to "OmniRoute",
            service.parseGitHubUrl("https://github.com/ayyannukadam001-commits/OmniRoute.git")
        )
    }

    @Test
    fun rejectsNonGitHubAndNestedUrls() {
        assertThrows(IllegalArgumentException::class.java) {
            service.parseGitHubUrl("https://example.com/owner/repo")
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.parseGitHubUrl("https://github.com/owner/repo/tree/main")
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.parseGitHubUrl("http://github.com/owner/repo")
        }
    }
}
