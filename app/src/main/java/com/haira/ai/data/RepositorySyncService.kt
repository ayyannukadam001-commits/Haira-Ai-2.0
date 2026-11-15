package com.haira.ai.data

import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Read-only connector for public GitHub repositories. It deliberately requests
 * no write permissions and never executes repository code.
 */
class RepositorySyncService {
    suspend fun syncPublicRepository(input: String): ConnectedRepositoryEntity =
        withContext(Dispatchers.IO) {
            val (owner, repository) = parseGitHubUrl(input)
            val apiBase = "https://api.github.com/repos/$owner/$repository"

            val metadataResponse = request(apiBase, "application/vnd.github+json", 1_000_000)
            require(metadataResponse.first == 200) {
                when (metadataResponse.first) {
                    404 -> "Repository not found or it is private. This connector currently supports public repositories only."
                    403 -> "GitHub rate limit reached. Try again later."
                    else -> "GitHub returned HTTP ${metadataResponse.first} while reading the repository."
                }
            }

            val metadata = JSONObject(metadataResponse.second)
            val fullName = metadata.optString("full_name", "$owner/$repository")
            val htmlUrl = metadata.optString("html_url", "https://github.com/$owner/$repository")
            val defaultBranch = metadata.optString("default_branch", "main")
            val description = metadata.optString("description", "").take(500)

            val readmeResponse = request(
                "$apiBase/readme",
                "application/vnd.github.raw",
                96 * 1024
            )
            val readme = when (readmeResponse.first) {
                200 -> readmeResponse.second
                    .replace(Regex("(?is)<script.*?</script>"), "")
                    .take(8_000)
                404 -> "No README.md found in this repository."
                else -> "Repository connected, but README sync returned HTTP ${readmeResponse.first}."
            }

            ConnectedRepositoryEntity(
                fullName = fullName,
                htmlUrl = htmlUrl,
                defaultBranch = defaultBranch,
                description = description,
                readmeExcerpt = readme
            )
        }

    internal fun parseGitHubUrl(input: String): Pair<String, String> {
        val uri = runCatching { URI(input.trim()) }.getOrNull()
            ?: error("Enter a valid GitHub repository URL.")
        require(
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals("github.com", ignoreCase = true) &&
                uri.userInfo == null &&
                (uri.port == -1 || uri.port == 443) &&
                uri.query == null &&
                uri.fragment == null
        ) {
            "Only canonical HTTPS GitHub repository URLs are supported."
        }

        val path = uri.path.removePrefix("/").removeSuffix("/")
        val segments = path.split("/")
        require(segments.size == 2) {
            "Use a repository URL like https://github.com/owner/repository."
        }
        val owner = segments[0]
        val repository = segments[1].removeSuffix(".git")
        val safeSegment = Regex("^[A-Za-z0-9_.-]{1,100}$")
        require(safeSegment.matches(owner) && safeSegment.matches(repository)) {
            "The GitHub owner or repository name contains unsupported characters."
        }
        require(owner != "." && owner != ".." && repository != "." && repository != "..") {
            "Invalid GitHub repository path."
        }
        return owner to repository
    }

    private fun request(
        url: String,
        accept: String,
        maxBytes: Int
    ): Pair<Int, String> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("Accept", accept)
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "HAIRA-AI-Android")
            instanceFollowRedirects = true
        }

        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= maxBytes) { "GitHub response exceeded the safe size limit." }
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }.orEmpty()
            return status to body
        } finally {
            connection.disconnect()
        }
    }
}
