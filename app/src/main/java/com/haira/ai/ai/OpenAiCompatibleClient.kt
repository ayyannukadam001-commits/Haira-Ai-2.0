package com.haira.ai.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class RemoteChatMessage(val role: String, val content: String)

data class RemoteProviderConfig(
    val baseUrl: String,
    val model: String,
    val apiKey: String,
    val enabled: Boolean = false
)

object OpenAiCompatibleClient {
    internal fun endpointFor(baseUrl: String): String {
        val uri = runCatching { URI(baseUrl.trim()) }.getOrNull()
            ?: throw IllegalArgumentException("Enter a valid HTTPS provider URL.")
        require(
            uri.scheme.equals("https", true) &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null &&
                uri.query == null &&
                uri.fragment == null
        ) { "Provider endpoint must be a valid HTTPS URL without credentials, query or fragment." }
        val path = uri.path.trimEnd('/')
        val endpointPath = when {
            path.endsWith("/v1/chat/completions") -> path
            path.endsWith("/v1") -> "$path/chat/completions"
            else -> "$path/v1/chat/completions"
        }
        return URI("https", null, uri.host, uri.port, endpointPath, null, null).toASCIIString()
    }

    suspend fun complete(
        config: RemoteProviderConfig,
        messages: List<RemoteChatMessage>
    ): String = withContext(Dispatchers.IO) {
        require(config.model.isNotBlank()) { "Enter a model name." }
        require(config.apiKey.isNotBlank()) { "Enter the provider's unified API key." }
        require(messages.isNotEmpty()) { "There are no messages to send." }

        val endpoint = endpointFor(config.baseUrl)
        val body = JSONObject().apply {
            put("model", config.model.trim())
            put("messages", JSONArray().apply {
                messages.forEach { message ->
                    put(JSONObject().put("role", message.role).put("content", message.content))
                }
            })
            put("stream", false)
            put("max_tokens", 384)
        }.toString()

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "HAIRA-AI-Android")
        }

        try {
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= 2 * 1024 * 1024) { "Provider response exceeded the 2 MB safety limit." }
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }.orEmpty()

            if (status !in 200..299) {
                val message = runCatching { JSONObject(response).optJSONObject("error")?.optString("message") }.getOrNull()
                error(message?.takeIf { it.isNotBlank() }?.take(300)
                    ?: "Provider request failed with HTTP $status.")
            }

            val choices = JSONObject(response).optJSONArray("choices")
                ?: error("Provider response did not contain choices.")
            val content = choices.optJSONObject(0)?.optJSONObject("message")?.opt("content")
            val answer = when (content) {
                is String -> content
                is JSONArray -> buildString {
                    for (i in 0 until content.length()) {
                        val item = content.optJSONObject(i)
                        if (item?.optString("type") == "text") append(item.optString("text"))
                    }
                }
                else -> ""
            }.trim()
            require(answer.isNotBlank()) { "Provider returned an empty assistant response." }
            answer
        } finally {
            connection.disconnect()
        }
    }
}
