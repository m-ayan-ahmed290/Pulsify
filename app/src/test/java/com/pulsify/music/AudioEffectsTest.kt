package com.pulsify.music

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.pulsify.music.playback.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class AudioEffectsTest {
    @Test fun effectsRoundTripPreservesCustomSettings() {
        val config = EffectsConfig(true, "Custom", listOf(4f,2f,-1f,0f,3f), 450, 200, false)
        assertEquals(config, EffectsConfig.decode(config.encode()))
    }
    @Test fun corruptAndOutOfRangeSettingsRecoverSafely() {
        assertEquals(EffectsConfig(), EffectsConfig.decode("invalid"))
        val safe = EffectsConfig(gains = listOf(Float.NaN, Float.POSITIVE_INFINITY, -50f, 50f), bass = -4, spatial = 9000).safe()
        assertEquals(listOf(0f,0f,-12f,12f,0f), safe.gains)
        assertEquals(0, safe.bass); assertEquals(1000, safe.spatial)
    }
    @Test fun frequencyMappingInterpolatesLogarithmicallyAndClampsEndpoints() {
        val config = EffectsConfig(gains = listOf(0f,4f,6f,2f,-2f))
        assertEquals(0f, gainAt(1f,config), .001f)
        assertEquals(-2f, gainAt(50000f,config), .001f)
        assertEquals(2f, gainAt(sqrt(60f * 230f),config), .001f)
        assertEquals(6f, gainAt(910f,config), .001f)
    }
    @Test fun headroomRespondsToBoostsAndCanBeBypassed() {
        val boosted = EffectsConfig(enabled = true, gains = listOf(6f,0f,0f,0f,0f))
        assertEquals(.501187f, effectsHeadroom(boosted), .0001f)
        assertEquals(1f, effectsHeadroom(boosted.copy(enabled = false)), 0f)
        assertEquals(1f, effectsHeadroom(boosted.copy(headroom = false)), 0f)
        assertTrue(effectsHeadroom(boosted.copy(bass = 1000)) < effectsHeadroom(boosted))
    }
    @Test fun settingsPersistWithoutAnAudioSessionAndDoNotAttenuateUnsupportedAudio() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("audio-effects", 0).edit().clear().commit()
        val first = AudioEffects(context)
        val config = EffectsConfig(enabled = true, preset = "Bass", gains = EffectsConfig.presets.getValue("Bass"), bass = 600)
        first.update(config); first.save(); first.release()
        val restored = AudioEffects(context)
        assertEquals(config, restored.config)
        assertFalse(restored.state().sessionReady)
        assertFalse(restored.state().equalizer)
        assertEquals(1f, restored.outputGain(), 0f)
        restored.release()
    }
}
