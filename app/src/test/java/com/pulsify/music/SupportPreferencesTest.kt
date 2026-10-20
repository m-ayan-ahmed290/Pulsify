package com.pulsify.music

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.pulsify.music.data.Preferences
import com.pulsify.music.domain.SUPPORT_INTERVAL_MS
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class SupportPreferencesTest {
    @Test fun claimsAreAtomicAndCooldownSurvivesNewPreferencesInstances() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val now = 1_000_000L
        val claims = coroutineScope { (1..8).map { async { Preferences(app).claimSupportPrompt(now) } }.awaitAll() }
        assertEquals(1, claims.count { it })
        assertFalse(Preferences(app).claimSupportPrompt(now + SUPPORT_INTERVAL_MS - 1))
        assertTrue(Preferences(app).claimSupportPrompt(now + SUPPORT_INTERVAL_MS))
        assertFalse(Preferences(app).claimSupportPrompt(now + SUPPORT_INTERVAL_MS))
    }
}
