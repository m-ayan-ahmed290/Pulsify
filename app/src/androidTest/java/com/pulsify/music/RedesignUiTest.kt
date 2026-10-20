package com.pulsify.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import org.junit.Rule
import org.junit.Test

class RedesignUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun navigationAndSoundStudioStayInsidePulsify() {
        compose.onNodeWithContentDescription("Open Sound Studio").performClick()
        compose.onNodeWithText("Sound Studio").assertIsDisplayed()
        compose.onNodeWithContentDescription("Enable sound engine").assertIsDisplayed()
        compose.onNode(hasText("Bass") and hasClickAction()).performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Enable sound engine").assertIsOn()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Search", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Find your next favorite.").assertIsDisplayed()
        compose.onNodeWithText("Library", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Your library").assertIsDisplayed()
        compose.onNodeWithText("Playlists", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Made by you.").assertIsDisplayed()
    }
    @Test fun searchInputRemainsVisibleWithKeyboardOpen() {
        compose.onNodeWithText("Search", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Search your music").performClick().performTextInput("A very long song name in my local music collection")
        compose.runOnIdle { WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).show(WindowInsetsCompat.Type.ime()) }
        compose.waitUntil(30000) { ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
        compose.onNodeWithText("Search your music").assertIsDisplayed()
        compose.onNodeWithContentDescription("Clear search").assertIsDisplayed().performClick()
        compose.onNodeWithText("Search your music").assertIsDisplayed()
    }
}
