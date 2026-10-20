@file:Suppress("DEPRECATION")
package com.pulsify.music.playback

import android.content.Context
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.log10
import kotlin.math.pow

/** Five musical anchor points are interpolated onto the device's actual EQ bands. */
data class EffectsConfig(val enabled: Boolean = false, val preset: String = "Flat",
    val gains: List<Float> = List(5) { 0f }, val bass: Int = 0, val spatial: Int = 0, val headroom: Boolean = true) {
    fun safe() = copy(gains = List(5) { gains.getOrElse(it) { 0f }.takeIf { n -> n.isFinite() }?.coerceIn(-12f, 12f) ?: 0f },
        bass = bass.coerceIn(0,1000), spatial = spatial.coerceIn(0,1000))
    fun encode(): String = JSONObject().put("enabled", enabled).put("preset", preset).put("gains", JSONArray(gains))
        .put("bass", bass).put("spatial", spatial).put("headroom", headroom).toString()
    companion object {
        val frequencies = listOf(60f, 230f, 910f, 3600f, 14000f)
        val presets = linkedMapOf("Flat" to listOf(0f,0f,0f,0f,0f), "Bass" to listOf(6f,4f,0f,1f,2f),
            "Warm" to listOf(3f,2f,1f,-1f,-2f), "Vocal" to listOf(-2f,0f,4f,3f,0f),
            "Electronic" to listOf(5f,3f,-1f,3f,5f), "Rock" to listOf(4f,2f,-1f,3f,4f),
            "Jazz" to listOf(2f,1f,2f,3f,2f), "Acoustic" to listOf(2f,2f,1f,3f,4f))
        fun decode(value: String?): EffectsConfig = runCatching {
            val json = JSONObject(value ?: "{}"); val array = json.optJSONArray("gains")
            EffectsConfig(json.optBoolean("enabled"), json.optString("preset", "Flat"), List(5) { array?.optDouble(it,0.0)?.toFloat() ?: 0f },
                json.optInt("bass"), json.optInt("spatial"), json.optBoolean("headroom", true)).safe()
        }.getOrDefault(EffectsConfig())
    }
}
fun gainAt(frequency: Float, config: EffectsConfig): Float {
    val f = frequency.coerceAtLeast(1f)
    val anchors = EffectsConfig.frequencies
    if(f <= anchors.first()) return config.gains.first()
    if(f >= anchors.last()) return config.gains.last()
    val index = (0..3).first { f <= anchors[it + 1] }
    val t = (log10(f) - log10(anchors[index])) / (log10(anchors[index + 1]) - log10(anchors[index]))
    return config.gains[index] + t * (config.gains[index + 1] - config.gains[index])
}
fun effectsHeadroom(config: EffectsConfig): Float = if(!config.enabled || !config.headroom) 1f else
    10.0.pow(-(config.gains.maxOrNull()?.coerceAtLeast(0f) ?: 0f) / 20.0 - config.bass / 1000.0 * .15).toFloat()

data class EffectsState(val config: EffectsConfig = EffectsConfig(), val sessionReady: Boolean = false,
    val equalizer: Boolean = false, val bass: Boolean = false, val spatial: Boolean = false, val note: String = "Start a song to activate audio effects.", val minGain: Float = -12f, val maxGain: Float = 12f) {
    companion object {
        fun fromBundle(bundle: Bundle) = EffectsState(EffectsConfig.decode(bundle.getString("config")), bundle.getBoolean("ready"),
            bundle.getBoolean("equalizer"), bundle.getBoolean("bass"), bundle.getBoolean("spatial"), bundle.getString("note").orEmpty(), bundle.getFloat("minGain", -12f), bundle.getFloat("maxGain", 12f))
    }
}
/** Lives exclusively in the playback service. Effects stay active with the screen off. */
@Suppress("DEPRECATION")
class AudioEffects(context: Context, private val onChanged: () -> Unit = {}) {
    private val store = context.getSharedPreferences("audio-effects", Context.MODE_PRIVATE)
    var config = EffectsConfig.decode(store.getString("config", null)); private set
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var spatial: Virtualizer? = null
    private var sessionId = 0
    private var error = ""
    fun attach(id: Int) {
        if(id == sessionId) return
        release(); sessionId = id
        if(id <= 0) return
        eq = runCatching { Equalizer(0, id) }.getOrNull()
        bass = runCatching { BassBoost(0, id) }.getOrNull()
        spatial = runCatching { Virtualizer(0, id) }.getOrNull()
        listOfNotNull(eq, bass, spatial).forEach { it.setControlStatusListener { _, control -> if(control) apply() } }
        config = fitDevice(config); apply()
    }
    private fun fitDevice(value: EffectsConfig): EffectsConfig {
        val safe = value.safe()
        val range = runCatching { eq?.bandLevelRange }.getOrNull() ?: return safe
        val minimum = (range[0] / 100f).coerceAtLeast(-12f)
        val maximum = (range[1] / 100f).coerceAtMost(12f)
        return if(minimum < maximum) safe.copy(gains = safe.gains.map { it.coerceIn(minimum,maximum) }) else safe
    }
    fun update(value: EffectsConfig) { config = fitDevice(value); apply() }
    fun save() { store.edit().putString("config", config.encode()).apply() }
    private fun controlled(effect: AudioEffect?) = runCatching { effect?.hasControl() == true }.getOrDefault(false)
    private fun apply() {
        error = ""
        eq?.let { effect -> runCatching {
            if(effect.hasControl()) {
                val range = effect.bandLevelRange
                for(band in 0 until effect.numberOfBands) {
                    val gain = gainAt(effect.getCenterFreq(band.toShort()) / 1000f, config)
                    effect.setBandLevel(band.toShort(), (gain * 100).toInt().coerceIn(range[0].toInt(), range[1].toInt()).toShort())
                }
                check(effect.setEnabled(config.enabled) == AudioEffect.SUCCESS)
            }
        }.onFailure { error = "The device could not apply every equalizer setting." } }
        bass?.let { effect -> runCatching {
            if(effect.hasControl() && effect.strengthSupported) { effect.setStrength(config.bass.toShort()); check(effect.setEnabled(config.enabled && config.bass > 0) == AudioEffect.SUCCESS) }
        }.onFailure { error = "Bass boost is unavailable for this output." } }
        spatial?.let { effect -> runCatching {
            if(effect.hasControl() && effect.strengthSupported) { effect.setStrength(config.spatial.toShort()); check(effect.setEnabled(config.enabled && config.spatial > 0) == AudioEffect.SUCCESS) }
        }.onFailure { error = "Spatial audio is unavailable for this output." } }
        onChanged()
    }
    fun outputGain(): Float {
        val available = state()
        return effectsHeadroom(config.copy(gains = if(available.equalizer) config.gains else List(5) { 0f }, bass = if(available.bass) config.bass else 0))
    }
    fun state(): EffectsState {
        val equalizer = controlled(eq) && runCatching { eq!!.numberOfBands > 0 && eq!!.bandLevelRange[0] < eq!!.bandLevelRange[1] }.getOrDefault(false)
        val hasBass = controlled(bass) && runCatching { bass?.strengthSupported == true }.getOrDefault(false)
        val hasSpatial = controlled(spatial) && runCatching { spatial?.strengthSupported == true }.getOrDefault(false)
        val note = when {
            sessionId <= 0 -> "Start a song to activate audio effects. Your settings are saved."
            error.isNotEmpty() -> error
            !equalizer && !hasBass && !hasSpatial -> "This device or output does not expose audio effects to Pulsify."
            config.enabled -> "Applied to Pulsify playback · Presets and levels are saved automatically."
            else -> "Turn on the sound engine to apply your settings."
        }
        val range = runCatching { eq?.bandLevelRange }.getOrNull()
        return EffectsState(config, sessionId > 0, equalizer, hasBass, hasSpatial, note, (range?.get(0)?.div(100f) ?: -12f).coerceAtLeast(-12f), (range?.get(1)?.div(100f) ?: 12f).coerceAtMost(12f))
    }
    fun bundle(): Bundle = state().let { state -> Bundle().apply {
        putString("config", config.encode()); putBoolean("ready", state.sessionReady); putBoolean("equalizer", state.equalizer)
        putBoolean("bass", state.bass); putBoolean("spatial", state.spatial); putString("note", state.note); putFloat("minGain", state.minGain); putFloat("maxGain", state.maxGain)
    } }
    fun release() {
        listOfNotNull(eq, bass, spatial).forEach { runCatching { it.release() } }
        eq = null; bass = null; spatial = null; sessionId = 0
    }
}
