package com.haira.ai.ai

import java.io.File

object LocalInference {
    private val lock = Any()
    @Volatile private var loaded = false

    val isLoaded: Boolean
        get() = loaded

    val backendAvailable: Boolean
        get() = try {
            System.loadLibrary("haira_llama")
            true
        } catch (_: Throwable) {
            false
        }

    fun load(path: String, context: Int = 2048): Boolean = synchronized(lock) {
        if (!backendAvailable || !File(path).isFile) return@synchronized false
        runCatching { nativeLoad(path, context).also { loaded = it } }
            .getOrDefault(false)
    }

    fun generate(prompt: String): String? = synchronized(lock) {
        if (loaded) runCatching { nativeGenerate(prompt, 256) }.getOrNull() else null
    }

    fun unload() = synchronized(lock) {
        if (loaded) runCatching { nativeUnload() }
        loaded = false
    }

    private external fun nativeLoad(path: String, context: Int): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String
    private external fun nativeUnload()
}
