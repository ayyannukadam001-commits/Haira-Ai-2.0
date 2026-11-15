package com.haira.ai

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
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
            composeRule.onAllNodesWithText("Model ready").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Message HAIRA").performTextInput(
            "Say hello in one short sentence."
        )
        composeRule.onNodeWithText("SEND").performClick()

        composeRule.waitUntil(timeoutMillis = 240_000) {
            composeRule.onAllNodesWithText("ASSISTANT").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
