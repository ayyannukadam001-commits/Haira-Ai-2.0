package com.haira.ai.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class ModelImporter(private val context: Context) {
    suspend fun import(uri: Uri): ModelEntity = withContext(Dispatchers.IO) {
        val suppliedName = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "model.gguf"

        // Treat provider-supplied display names as untrusted input; never let them
        // create paths outside the app's model directory.
        val safeName = suppliedName
            .substringAfterLast('/')
            .substringAfterLast('\\\\')
            .filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
            .take(120)
            .ifBlank { "model.gguf" }
        require(safeName.endsWith(".gguf", ignoreCase = true)) {
            "Select a .gguf file."
        }

        val dir = File(context.getExternalFilesDir(null), "HAIRA_Models")
        check(dir.exists() || dir.mkdirs()) { "Unable to create model storage directory." }
        val target = File(dir, "${System.currentTimeMillis()}-$safeName")
        val temp = File(dir, "${target.name}.partial")

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().buffered().use { output -> input.copyTo(output) }
            } ?: error("Cannot read selected file.")

            require(temp.length() > 1024L * 1024L) {
                "File is too small to be a GGUF model."
            }

            val header = ByteArray(4)
            temp.inputStream().use { input ->
                require(
                    input.read(header) == 4 &&
                        header.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))
                ) { "Invalid GGUF header." }
            }

            val digest = MessageDigest.getInstance("SHA-256")
            temp.inputStream().buffered().use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }

            val expected = when (safeName) {
                "SmolLM2-360M-Instruct-Q4_K_M.gguf" ->
                    "2fa3f013dcdd7b99f9b237717fa0b12d75bbb89984cc1274be1471a465bac9c2"
                "Qwen3-0.6B.Q4_K_M.gguf" ->
                    "7af3fdf842f87b24672f8a7f1dd50404043f0bfb71093ff91c31d2b49df4631d"
                else -> null
            }
            require(expected == null || expected.equals(hash, ignoreCase = true)) {
                "SHA-256 checksum mismatch; file rejected."
            }

            check(temp.renameTo(target)) { "Unable to finalize imported model file." }
            ModelEntity(
                path = target.absolutePath,
                displayName = safeName,
                sizeBytes = target.length(),
                sha256 = hash
            )
        } finally {
            if (temp.exists()) temp.delete()
        }
    }
}
