package com.example.flowmoney

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.flowmoney.ui.theme.FlowMoneyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GreetingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun greeting_displaysCorrectText() {
        // Set the content of the Compose rule
        composeTestRule.setContent {
            FlowMoneyTheme {
                Greeting("Android")
            }
        }

        // Verify that the text "Hello Android!" is displayed
        composeTestRule.onNodeWithText("Hello Android!").assertExists()
    }
}
