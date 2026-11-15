package com.haira.ai

import android.content.Context
import android.os.StatFs
import com.haira.ai.data.ModelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Installs the verified default GGUF from APK assets into app-private storage.
 * The asset is intentionally uncompressed by the Android Gradle configuration.
 */
class BundledModelInstaller(private val context: Context) {
    companion object {
        private const val ASSET_NAME = "default-model.gguf"
        private const val MODEL_NAME = "SmolLM2-360M-Instruct"
        private const val EXPECTED_BYTES = 272_427_976L
        private const val EXPECTED_SHA256 =
            "2fa3f013dcdd7b99f9b237717fa0b12d75bbb89984cc1274be1471a465bac9c2"
    }

    suspend fun install(onStage: suspend (String) -> Unit = {}): ModelEntity =
        withContext(Dispatchers.IO) {
            val modelDir = File(context.filesDir, "models").apply {
                if (!exists() && !mkdirs()) error("Unable to create private model directory")
            }
            val target = File(modelDir, "smollm2-360m-instruct-q4_k_m.gguf")
            onStage("Verifying bundled model")
            if (target.isFile && target.length() == EXPECTED_BYTES &&
                sha256(target) == EXPECTED_SHA256
            ) {
                return@withContext entity(target)
            }

            val available = StatFs(modelDir.absolutePath).availableBytes
            require(available >= EXPECTED_BYTES + 32L * 1024L * 1024L) {
                "Not enough free storage. Free at least 305 MB and reopen HAIRA."
            }

            onStage("Installing bundled model")
            val temp = File(modelDir, "${target.name}.partial")
            try {
                context.assets.open(ASSET_NAME).use { input ->
                    temp.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                onStage("Verifying bundled model")
                check(temp.length() == EXPECTED_BYTES) {
                    "Bundled model size mismatch; refusing to install."
                }
                val actualHash = sha256(temp)
                check(actualHash == EXPECTED_SHA256) {
                    "Bundled model checksum mismatch; refusing to install."
                }
                if (target.exists()) check(target.delete()) {
                    "Unable to replace the previous incomplete model file."
                }
                check(temp.renameTo(target)) { "Unable to finalize bundled model file" }
            } catch (error: Exception) {
                temp.delete()
                throw error
            }
            entity(target)
        }

    private fun entity(file: File) = ModelEntity(
        path = file.absolutePath,
        displayName = MODEL_NAME,
        sizeBytes = file.length(),
        sha256 = EXPECTED_SHA256
    )

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
