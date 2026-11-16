package com.haira.ai.data

import android.net.Uri
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Read-only GitHub connector. Private repositories require a user-supplied token
 * with Contents: read-only permission. Repository files are treated as untrusted data
 * and are never executed.
 */
class RepositorySyncService {
    suspend fun syncPublicRepository(input: String): ConnectedRepositoryEntity =
        syncRepository(input, null)

    suspend fun syncRepository(input: String, token: String?): ConnectedRepositoryEntity =
        withContext(Dispatchers.IO) {
            val (owner, repository) = parseGitHubUrl(input)
            val apiBase = "https://api.github.com/repos/$owner/$repository"
            val metadataResponse = request(apiBase, "application/vnd.github+json", 1_000_000, token)
            require(metadataResponse.first == 200) {
                when (metadataResponse.first) {
                    401, 403 -> "GitHub access denied. Check token permissions or API rate limits."
                    404 -> "Repository not found, or private repository access is not authorized."
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
                96 * 1024,
                token
            )
            val readme = when (readmeResponse.first) {
                200 -> readmeResponse.second
                    .replace(Regex("(?is)<script.*?</script>"), "")
                    .take(8_000)
                404 -> "No README.md found in this repository."
                else -> "Repository metadata connected; README sync returned HTTP ${readmeResponse.first}."
            }

            val source = StringBuilder()
            val treeUrl = "$apiBase/git/trees/${Uri.encode(defaultBranch)}?recursive=1"
            val treeResponse = request(treeUrl, "application/vnd.github+json", 2 * 1024 * 1024, token)
            if (treeResponse.first == 200) {
                val entries = JSONObject(treeResponse.second).optJSONArray("tree") ?: JSONArray()
                val allowed = setOf("kt", "kts", "java", "xml", "gradle", "md", "json", "yml", "yaml", "toml", "properties", "txt")
                val selected = (0 until entries.length()).mapNotNull { index ->
                    entries.optJSONObject(index)?.takeIf { it.optString("type") == "blob" }?.let { entry ->
                        val path = entry.optString("path")
                        val extension = path.substringAfterLast('.', "").lowercase()
                        val lower = path.lowercase()
                        if (extension in allowed &&
                            !lower.contains("/build/") &&
                            !lower.startsWith(".git/") &&
                            !lower.contains("secret") &&
                            !lower.contains(".env") &&
                            !lower.contains("keystore") &&
                            entry.optLong("size", Long.MAX_VALUE) in 1..(64 * 1024)
                        ) path else null
                    }
                }.take(18)

                for (path in selected) {
                    if (source.length >= 80 * 1024) break
                    val encodedPath = path.split("/").joinToString("/") { Uri.encode(it) }
                    val fileResponse = request(
                        "$apiBase/contents/$encodedPath?ref=${Uri.encode(defaultBranch)}",
                        "application/vnd.github.raw",
                        64 * 1024,
                        token
                    )
                    if (fileResponse.first == 200 && fileResponse.second.isNotBlank()) {
                        val remaining = (80 * 1024 - source.length).coerceAtLeast(0)
                        if (remaining > 0) {
                            source.append("\n--- FILE: ").append(path).append(" ---\n")
                                .append(fileResponse.second.take(remaining))
                                .append("\n")
                        }
                    }
                }
            }

            ConnectedRepositoryEntity(
                fullName = fullName,
                htmlUrl = htmlUrl,
                defaultBranch = defaultBranch,
                description = description,
                readmeExcerpt = readme,
                sourceExcerpt = source.toString().take(80 * 1024)
            )
        }

    internal fun parseGitHubUrl(input: String): Pair<String, String> {
        val uri = runCatching { URI(input.trim()) }.getOrNull()
            ?: throw IllegalArgumentException("Enter a valid GitHub repository URL.")
        require(
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals("github.com", ignoreCase = true) &&
                uri.userInfo == null &&
                (uri.port == -1 || uri.port == 443) &&
                uri.query == null &&
                uri.fragment == null
        ) { "Only canonical HTTPS GitHub repository URLs are supported." }

        val path = uri.path.removePrefix("/").removeSuffix("/")
        val segments = path.split("/")
        require(segments.size == 2) { "Use a repository URL like https://github.com/owner/repository." }
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

    private fun request(url: String, accept: String, maxBytes: Int, token: String?): Pair<Int, String> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("Accept", accept)
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "HAIRA-AI-Android")
            token?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Authorization", "Bearer ${it.trim()}") }
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
