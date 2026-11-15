package com.haira.ai.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceOutput(context: Context, private val onReadyChanged: (Boolean) -> Unit = {}) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    @Volatile private var ready = false
    @Volatile private var pendingText: String? = null

    val isReady: Boolean
        get() = ready

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        onReadyChanged(ready)
        if (ready) {
            tts?.language = Locale.getDefault()
            pendingText?.let { text ->
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "haira-tts")
                pendingText = null
            }
        }
    }

    @Synchronized
    fun speak(text: String) {
        if (text.isBlank()) return
        if (!ready) {
            pendingText = text
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "haira-tts")
    }

    @Synchronized
    fun stop() {
        pendingText = null
        tts?.stop()
    }

    fun language(locale: Locale) {
        if (ready) tts?.language = locale
    }

    @Synchronized
    fun close() {
        pendingText = null
        ready = false
        onReadyChanged(false)
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
