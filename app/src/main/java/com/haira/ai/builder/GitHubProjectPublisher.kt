package com.haira.ai.builder

import android.net.Uri
import com.haira.ai.data.RepositorySyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import android.util.Base64

/** Publishes a generated starter project to a user-authorized repository and dispatches its cloud APK build. */
class GitHubProjectPublisher {
    suspend fun publishAndBuild(
        repositoryUrl: String,
        token: String,
        appName: String,
        packageId: String
    ): String = withContext(Dispatchers.IO) {
        require(token.isNotBlank()) { "Save a GitHub token in Actions first." }
        require(ProjectZipGenerator.isValidPackageId(packageId)) { "Invalid Android package ID." }
        val (owner, repo) = RepositorySyncService().parseGitHubUrl(repositoryUrl)
        val api = "https://api.github.com/repos/$owner/$repo"
        val headers = mapOf(
            "Authorization" to "Bearer ${token.trim()}",
            "Accept" to "application/vnd.github+json",
            "X-GitHub-Api-Version" to "2022-11-28",
            "User-Agent" to "HAIRA-AI-Android"
        )

        val metadata = request("$api", "GET", null, headers)
        require(metadata.first in 200..299) { "GitHub repository access failed (HTTP ${metadata.first})." }
        val branch = JSONObject(metadata.second).optString("default_branch").ifBlank { "main" }

        val archive = ByteArrayOutputStream().also { ProjectZipGenerator.write(it, appName, packageId) }.toByteArray()
        require(archive.size <= 8 * 1024 * 1024) { "Generated project archive is too large for direct GitHub upload." }
        val folder = appName.lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-').take(32).ifBlank { "android-project" }
        val archivePath = "haira-generated-projects/$folder-${System.currentTimeMillis()}/project.zip"
        val uploadBody = JSONObject()
            .put("message", "Add generated Android project: $appName")
            .put("branch", branch)
            .put("content", Base64.encodeToString(archive, Base64.NO_WRAP))
            .toString()
        val upload = request("$api/contents/${archivePath.split("/").joinToString("/") { Uri.encode(it) }}", "PUT", uploadBody, headers)
        require(upload.first in 200..299) {
            val detail = runCatching { JSONObject(upload.second).optString("message") }.getOrNull()
            detail?.takeIf { it.isNotBlank() } ?: "Unable to upload project to GitHub (HTTP ${upload.first}). Check Contents: write permission."
        }

        val dispatchBody = JSONObject()
            .put("ref", branch)
            .put("inputs", JSONObject().put("project_archive_path", archivePath).put("project_name", appName.take(80)))
            .toString()
        val dispatch = request("$api/actions/workflows/generated-project-build.yml/dispatches", "POST", dispatchBody, headers)
        require(dispatch.first == 204 || dispatch.first in 200..299) {
            val detail = runCatching { JSONObject(dispatch.second).optString("message") }.getOrNull()
            detail?.takeIf { it.isNotBlank() } ?: "Project uploaded, but GitHub could not start the build (HTTP ${dispatch.first}). Check Actions: write and Workflows: write permissions."
        }

        // The dispatch endpoint returns 204; fetch the latest matching workflow run for a useful link.
        val runs = request("$api/actions/workflows/generated-project-build.yml/runs?branch=${Uri.encode(branch)}&event=workflow_dispatch&per_page=1", "GET", null, headers)
        if (runs.first in 200..299) {
            val item = JSONObject(runs.second).optJSONArray("workflow_runs")?.optJSONObject(0)
            val runUrl = item?.optString("html_url").orEmpty()
            if (runUrl.startsWith("https://github.com/")) return@withContext runUrl
        }
        "https://github.com/$owner/$repo/actions/workflows/generated-project-build.yml"
    }

    private fun request(
        url: String,
        method: String,
        body: String?,
        headers: Map<String, String>
    ): Pair<Int, String> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = false
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(256 * 1024) }.orEmpty()
            return status to response
        } finally {
            connection.disconnect()
        }
    }
}
