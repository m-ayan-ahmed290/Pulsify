package com.pulsify.music

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pulsify.music.data.Settings
import com.pulsify.music.domain.LyricsDocument
import com.pulsify.music.ui.LyricsContent
import com.pulsify.music.ui.PulsifyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LyricsUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun highlightsPlaybackLineAndSeeksWhenAnotherLineIsTapped() {
        val position = mutableLongStateOf(1500)
        var seek = -1L
        compose.setContent {
            PulsifyTheme(Settings()) {
                LyricsContent(LyricsDocument("song", "[00:01]First verse\n[00:05]Second verse"), position.longValue, "Song", {}, { seek = it }, {}, {})
            }
        }
        compose.onNodeWithText("First verse").assertIsSelected()
        compose.onNodeWithText("Second verse").assertIsNotSelected().performClick()
        compose.runOnIdle { assertEquals(5000L, seek); position.longValue = seek }
        compose.onNodeWithText("Second verse").assertIsSelected()
        compose.onNodeWithText("First verse").assertIsNotSelected()
    }
    @Test fun timingControlRequestsPerSongDelay() {
        var delay = 0L
        compose.setContent { PulsifyTheme(Settings()) { LyricsContent(LyricsDocument("song", "[00:01]First verse"), 1000, "Song", {}, {}, { delay = it }, {}) } }
        compose.onNodeWithText("Timing").performClick()
        compose.onNodeWithText("+0.25s").performClick()
        compose.runOnIdle { assertEquals(250L, delay) }
    }
}
