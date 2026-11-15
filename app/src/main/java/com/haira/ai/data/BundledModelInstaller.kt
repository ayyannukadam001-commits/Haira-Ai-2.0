package com.haira.ai.data

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class BundledModelInstaller(private val context: Context) {
    suspend fun install(): ModelEntity = withContext(Dispatchers.IO) {
        val fileName = "SmolLM2-360M-Instruct-Q4_K_M.gguf"
        val expectedHash = "2fa3f013dcdd7b99f9b237717fa0b12d75bbb89984cc1274be1471a465bac9c2"
        val targetDir = File(context.filesDir, "HAIRA_Models").apply { mkdirs() }
        val target = File(targetDir, fileName)

        if (!target.isFile || sha256(target) != expectedHash) {
            val available = StatFs(context.filesDir.absolutePath).availableBytes
            require(available >= 450L * 1024L * 1024L) {
                "At least 450 MB free storage is required to install the bundled model."
            }
            val temp = File(targetDir, "$fileName.part")
            if (temp.exists()) temp.delete()
            context.assets.open("default-model.gguf").use { input ->
                temp.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            require(sha256(temp) == expectedHash) {
                "Bundled SmolLM2 checksum verification failed."
            }
            if (target.exists()) target.delete()
            check(temp.renameTo(target)) { "Could not move the verified model into place." }
        }

        ModelEntity(
            path = target.absolutePath,
            displayName = fileName,
            sizeBytes = target.length(),
            sha256 = expectedHash
        )
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
