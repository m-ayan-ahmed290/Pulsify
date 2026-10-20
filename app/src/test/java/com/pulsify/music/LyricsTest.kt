package com.pulsify.music

import com.pulsify.music.domain.*
import org.junit.Assert.*
import org.junit.Test

class LyricsTest {
    @Test fun acceptsLyricsExtensionsRegardlessOfProviderMimeType() {
        assertTrue(isLyricsFilename("Song.LRC")); assertTrue(isLyricsFilename("words.txt"))
        assertFalse(isLyricsFilename("song.mp3")); assertFalse(isLyricsFilename("song.lrc.exe"))
    }
    @Test fun parsesFractionsRepeatedTimestampsAndSortsLines() {
        val parsed = parseLyrics("\uFEFF[ti:Song]\r\n[00:20.25][00:40.250]Chorus\r\n[00:01.5]Verse\n[00:03]Next")
        assertEquals(listOf(1500L,3000L,20250L,40250L), parsed.lines.map { it.timeMs })
        assertEquals("Chorus", parsed.lines.last().text)
    }
    @Test fun handlesOffsetsHoursDuplicateTimesAndInstrumentalGaps() {
        val parsed = parseLyrics("[offset:+500]\n[01:00:01.001]Late\n[00:10]Original\n[00:10]Translation\n[00:12]\n[00:99]Invalid")
        assertEquals(listOf(9500L,11500L,3600501L), parsed.lines.map { it.timeMs })
        assertEquals("Original\nTranslation", parsed.lines.first().text)
        assertEquals("", parsed.lines[1].text)
    }
    @Test fun highlightAndSeekUseTheSameTimingAdjustment() {
        val parsed = parseLyrics("[00:01]First\n[00:03]Second")
        assertEquals(-1, parsed.activeIndex(999)); assertEquals(0, parsed.activeIndex(1000))
        assertEquals(1, parsed.activeIndex(99999)); assertEquals(0, parsed.activeIndex(3000,500))
        assertEquals(3500L, parsed.seekPosition(1,500)); assertEquals(1,parsed.activeIndex(3500,500))
        assertEquals(0L, parsed.seekPosition(0,-2000))
    }
    @Test fun retainsPlainTextAndIgnoresMalformedTags() {
        val parsed = parseLyrics("[ar:Artist]\nHello\n[not a time]World")
        assertFalse(parsed.synced); assertEquals("Hello\n[not a time]World", parsed.plainText)
        assertEquals(-1, parsed.activeIndex(1000))
    }
}
