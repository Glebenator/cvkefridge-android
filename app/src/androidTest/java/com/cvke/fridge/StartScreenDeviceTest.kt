package com.cvke.fridge

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class StartScreenDeviceTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun startScreenShowsTitleAndVersion() {
        rule.onNodeWithTag("start_title").assertIsDisplayed()
        rule.onNodeWithText("Version 0.1.0 (1)").assertExists()
    }
}
