package com.pulsify.music

import com.pulsify.music.domain.*
import org.junit.Assert.*
import org.junit.Test

class ListeningToolsTest {
    @Test fun repeatSegmentRequiresOrderedPointsAndLoopsAtBoundary() {
        val first = RepeatSegment().mark(10000, 60000)
        assertEquals(10000L, first.start); assertFalse(first.active)
        val loop = first.mark(20000, 60000)
        assertTrue(loop.active); assertFalse(loop.shouldSeek(19999)); assertTrue(loop.shouldSeek(20000))
        assertEquals(RepeatSegment(30000), loop.mark(30000, 60000))
    }
    @Test fun shortOrReversedSegmentsRestartTheFirstPointAndClampToTrack() {
        assertEquals(RepeatSegment(10500), RepeatSegment(10000).mark(10500,60000))
        assertEquals(RepeatSegment(3000), RepeatSegment(10000).mark(3000,60000))
        assertEquals(60000L, RepeatSegment().mark(90000,60000).start)
        assertEquals(0L, RepeatSegment().mark(-100,60000).start)
    }
    @Test fun recentSearchesTrimDeduplicateAndKeepLatestEight() {
        assertEquals(listOf("Jazz", "Night"), rememberedSearches(listOf("jazz", "Night"), " Jazz "))
        val previous = (1..8).map { "Search $it" }
        val next = rememberedSearches(previous, "Fresh")
        assertEquals(8, next.size); assertEquals("Fresh", next.first()); assertFalse("Search 8" in next)
        assertEquals(next, rememberedSearches(next, "  "))
    }
}
