package com.haira.ai.next

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HairaNextRuntimeSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesNextApplicationAndRendersHomeShell() {
        assertEquals("com.haira.ai.next", composeRule.activity.packageName)
        composeRule.onNodeWithText("YOUR PERSONAL AI ASSISTANT").assertIsDisplayed()
    }
}
