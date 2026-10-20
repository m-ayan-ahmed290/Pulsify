package com.pulsify.music

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pulsify.music.data.Settings
import com.pulsify.music.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertFalse

class NavigationMotionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun navigationCapsuleHasDistinctIntermediateAndSettledFrames() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            var route by remember { mutableStateOf("home") }
            PulsifyTheme(Settings()) { Box(Modifier.testTag("dock")) { NavigationDock(route, Playback(), { route = it }, {}, {}, {}, {}) } }
        }
        compose.onNodeWithText("Search", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(96)
        val moving = compose.onNodeWithTag("dock").captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(1000)
        val settled = compose.onNodeWithTag("dock").captureToImage().asAndroidBitmap()
        assertFalse("The selected capsule must visibly animate between tabs", moving.sameAs(settled))
    }
}
