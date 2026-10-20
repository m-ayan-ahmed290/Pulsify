package com.pulsify.music

import com.pulsify.music.playback.StoredSession
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = android.app.Application::class)
class StoredSessionTest {
    @Test fun roundTripPreservesQueueIndexPositionAndModes() {
        val saved = StoredSession(listOf("content://song/1", "content://song/2"), 1, 52341, true, 2)
        assertEquals(saved, StoredSession.decode(saved.encode()))
    }
    @Test fun corruptAndEmptySessionsAreDiscarded() {
        assertNull(StoredSession.decode("invalid"))
        assertNull(StoredSession.decode("{}"))
        assertNull(StoredSession.decode("{\"queue\":[]}"))
    }
    @Test fun invalidPlaybackValuesAreClamped() {
        val saved = StoredSession.decode("{\"queue\":[\"content://one\"],\"index\":999,\"position\":-50,\"repeat\":99}")!!
        assertEquals(0, saved.index)
        assertEquals(0L, saved.position)
        assertEquals(2, saved.repeat)
    }
}
