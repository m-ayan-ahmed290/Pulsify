package com.pulsify.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pulsify.music.data.Settings
import com.pulsify.music.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class InputDialogTest {
    @get:Rule val compose = createComposeRule()
    @Test fun focusedPlaylistInputAndSaveRemainVisible() {
        var saved = ""
        compose.setContent { PulsifyTheme(Settings()) { NameDialog("New playlist", onDismiss = {}, onSave = { saved = it }) } }
        compose.onNodeWithText("Playlist name").performClick().performTextInput("My late night mix")
        compose.onNodeWithText("Playlist name").assertIsDisplayed()
        compose.onNodeWithText("Save").performScrollTo().assertIsDisplayed().performClick()
        assertEquals("My late night mix", saved)
    }
}
