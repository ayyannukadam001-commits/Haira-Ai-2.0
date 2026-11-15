package com.haira.ai.ai

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class OpenAiCompatibleClient {
    suspend fun complete(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<Pair<String, String>>
    ): String = withContext(Dispatchers.IO) {
        val parsedBase = Uri.parse(baseUrl.trim())
        require(
            parsedBase.scheme.equals("https", ignoreCase = true) &&
                !parsedBase.host.isNullOrBlank() &&
                parsedBase.userInfo == null &&
                parsedBase.query == null &&
                parsedBase.fragment == null
        ) {
            "The online provider endpoint must be a valid HTTPS URL without embedded credentials."
        }
        require(apiKey.isNotBlank()) { "Online provider API key is missing." }
        require(model.isNotBlank()) { "Online provider model is missing." }
        require(messages.isNotEmpty()) { "There is no chat context to send." }

        val normalized = baseUrl.trim().trimEnd('/')
        val endpoint = when {
            normalized.endsWith("/chat/completions") -> normalized
            normalized.endsWith("/v1") -> "$normalized/chat/completions"
            else -> "$normalized/v1/chat/completions"
        }

        val messageArray = JSONArray()
        messages.takeLast(12).forEach { (role, text) ->
            require(role in setOf("system", "user", "assistant")) { "Unsupported chat role." }
            messageArray.put(
                JSONObject()
                    .put("role", role)
                    .put("content", text.take(5_000))
            )
        }

        val payload = JSONObject()
            .put("model", model)
            .put("messages", messageArray)
            .put("stream", false)
            .put("max_tokens", 256)
            .toString()
            .toByteArray(Charsets.UTF_8)

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "HAIRA-AI-Android")
            instanceFollowRedirects = false
            setFixedLengthStreamingMode(payload.size)
        }

        try {
            connection.outputStream.use { it.write(payload) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.use { readLimited(it, 1024 * 1024) }.orEmpty()

            if (status !in 200..299) {
                val message = runCatching {
                    JSONObject(responseBody).optString("error").ifBlank {
                        JSONObject(responseBody).optString("message")
                    }
                }.getOrDefault("")
                throw IllegalStateException(
                    when (status) {
                        401, 403 -> "Provider authentication failed. Check the API key and permissions."
                        404 -> "Provider endpoint or model was not found. Check the URL and model ID."
                        429 -> "Provider rate limit reached. Try again later or choose another model."
                        else -> "Provider request failed (HTTP $status). " + message.take(240)
                    }
                )
            }

            val root = JSONObject(responseBody)
            val choices = root.optJSONArray("choices")
                ?: error("Provider response did not include choices.")
            require(choices.length() > 0) { "Provider returned no answer choices." }
            val message = choices.getJSONObject(0).optJSONObject("message")
                ?: error("Provider response did not include a message.")
            val content = message.opt("content")
            val answer = when (content) {
                is String -> content
                is JSONArray -> buildString {
                    for (index in 0 until content.length()) {
                        val part = content.optJSONObject(index) ?: continue
                        if (part.optString("type") == "text") append(part.optString("text"))
                    }
                }
                else -> ""
            }.trim()
            require(answer.isNotBlank()) { "Provider returned an empty text response." }
            answer.take(20_000)
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(input: java.io.InputStream, maxBytes: Int): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) { "Provider response exceeded the safe size limit." }
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }
}
