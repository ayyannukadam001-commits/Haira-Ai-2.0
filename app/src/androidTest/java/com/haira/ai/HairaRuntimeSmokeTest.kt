package com.haira.ai

import android.content.Intent
import android.speech.RecognizerIntent
import com.haira.ai.voice.VoiceOutput
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HairaRuntimeSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bundledModelLoadsAndGeneratesAnAssistantResponse() {
        composeRule.waitUntil(timeoutMillis = 240_000) {
            composeRule.onAllNodesWithText("MODEL READY").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Message HAIRA • /image prompt to generate").performTextInput(
            "Say hello in one short sentence."
        )
        composeRule.onNodeWithText("↑").performClick()

        composeRule.waitUntil(timeoutMillis = 240_000) {
            composeRule.onAllNodesWithTag("assistant-response").fetchSemanticsNodes().any { node ->
                node.config[SemanticsProperties.Text]
                    .any { text -> text.text.isNotBlank() }
            }
        }

        val responseText = composeRule.onAllNodesWithTag("assistant-response")
            .fetchSemanticsNodes()
            .first()
            .config[SemanticsProperties.Text]
            .joinToString(" ") { it.text }
        assertTrue("Assistant response must not be blank", responseText.isNotBlank())
        assertFalse(
            "Assistant response must not echo the test prompt",
            responseText.contains("Say hello in one short sentence.", ignoreCase = true)
        )
    }
    @Test
    fun voiceServicesCanInitializeAndAcceptSpeechOutput() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val ttsReady = CountDownLatch(1)
        val voice = VoiceOutput(context) { ready -> if (ready) ttsReady.countDown() }
        try {
            val recognizerAvailable = context.packageManager.resolveActivity(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
                0
            ) != null
            assumeTrue("No speech recognition service is installed in this emulator.", recognizerAvailable)
            assumeTrue("Text-to-speech service did not initialize in this emulator.", ttsReady.await(20, TimeUnit.SECONDS))
            voice.speak("HAIRA voice test.")
            voice.stop()
            assertTrue(voice.isReady)
        } finally {
            voice.close()
        }
    }

}
