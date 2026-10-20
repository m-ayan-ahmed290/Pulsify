package com.pulsify.music

import com.pulsify.music.domain.*
import org.junit.Assert.*
import org.junit.Test

class PlaybackRulesTest {
    @Test fun reorderPreservesEveryQueueItemAndHandlesInvalidMoves() {
        val queue = listOf("a", "b", "c")
        assertEquals(listOf("b", "c", "a"), moved(queue, 0, 2))
        assertEquals(queue, moved(queue, -1, 2))
        assertEquals(queue, moved(queue, 0, 3))
        assertEquals(queue, moved(queue, 1, 1))
    }
    @Test fun briefPreviewsDoNotIncrementHistory() {
        assertFalse(qualifiesForPlay(5000, 240000))
        assertFalse(qualifiesForPlay(29999, 240000))
        assertTrue(qualifiesForPlay(30000, 240000))
    }
    @Test fun shortTracksUseHalfDurationWithOneSecondMinimum() {
        assertFalse(qualifiesForPlay(9999, 20000))
        assertTrue(qualifiesForPlay(10000, 20000))
        assertFalse(qualifiesForPlay(999, 500))
        assertTrue(qualifiesForPlay(1000, 500))
    }
}
