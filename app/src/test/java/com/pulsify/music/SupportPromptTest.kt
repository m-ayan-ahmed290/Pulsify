package com.pulsify.music

import com.pulsify.music.domain.*
import org.junit.Assert.*
import org.junit.Test

class SupportPromptTest {
    @Test fun firstVisitIsEligibleAndReopensAreSilentUntilSevenDays() {
        val first = 1_000_000L
        assertTrue(supportPromptDue(first, null))
        assertFalse(supportPromptDue(first, first))
        assertFalse(supportPromptDue(first + SUPPORT_INTERVAL_MS - 1, first))
        assertTrue(supportPromptDue(first + SUPPORT_INTERVAL_MS, first))
        assertTrue(supportPromptDue(first + 2 * SUPPORT_INTERVAL_MS, first))
    }
    @Test fun clockMovingBackwardCannotTriggerAnotherPrompt() {
        assertFalse(supportPromptDue(1000, 2000))
    }
}
