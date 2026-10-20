package com.pulsify.music.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulsify.music.playback.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable fun EffectsScreen(vm: MusicViewModel, onBack: () -> Unit) {
    val state by vm.effects.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { while(true) { vm.refreshEffects(); delay(2000) } }
    EffectsContent(state, playback.item?.mediaMetadata?.title?.toString(), vm::updateEffects, onBack)
}
@Composable fun EffectsContent(state: EffectsState, title: String?, onUpdate: (EffectsConfig) -> Unit, onBack: () -> Unit) {
    val config = state.config
    val eqEnabled = config.enabled && (!state.sessionReady || state.equalizer)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
            Column(Modifier.weight(1f).padding(start = 4.dp)) { Text("PULSIFY AUDIO", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall); Text("Sound Studio", style = MaterialTheme.typography.headlineMedium) }
            ActionIcon(Icons.Rounded.RestartAlt, "Reset audio effects") { onUpdate(EffectsConfig(enabled = config.enabled)) }
        } }
        item { Entrance {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.GraphicEq, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) { Text("Your sound, dialed in.", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium); Text(if(config.enabled) "Sound engine on" else "Sound engine off", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Switch(config.enabled, onCheckedChange = { onUpdate(config.copy(enabled = it)) }, modifier = Modifier.semantics { contentDescription = "Enable sound engine" })
                    }
                    Text(state.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if(title != null) Text("Listening to $title", maxLines = 2, style = MaterialTheme.typography.labelMedium)
                }
            }
        } }
        item {
            Text("Pick a feeling", style = MaterialTheme.typography.titleLarge)
            Text("Start with a preset. Make it your own.", Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EffectsConfig.presets.forEach { (name,gains) -> FilterChip(config.preset == name, onClick = { onUpdate(config.copy(enabled = true, preset = name, gains = gains)) }, label = { Text(name) }) }
            }
        }
        item { Entrance(1) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(18.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("5-band equalizer", fontWeight = FontWeight.Bold); Text(config.preset, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                    val line = MaterialTheme.colorScheme.primary
                    Canvas(Modifier.fillMaxWidth().height(70.dp).padding(top = 14.dp)) {
                        val zero = size.height / 2
                        drawLine(line.copy(alpha = .12f), Offset(0f,zero), Offset(size.width,zero))
                        val path = Path()
                        config.gains.forEachIndexed { i,gain -> val x = size.width * i / 4; val y = zero - gain / 12f * zero * .8f; if(i == 0) path.moveTo(x,y) else path.lineTo(x,y); drawCircle(line, 3.dp.toPx(), Offset(x,y)) }
                        drawPath(path, line, style = Stroke(2.dp.toPx()))
                    }
                    Row(Modifier.fillMaxWidth().height(220.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("60", "230", "910", "3.6k", "14k").forEachIndexed { index,label ->
                            EqualizerFader(label, config.gains[index], eqEnabled, Modifier.weight(1f), state.minGain, state.maxGain) { value -> onUpdate(config.copy(preset = "Custom", gains = config.gains.toMutableList().apply { set(index,value) })) }
                        }
                    }
                    Text(if(state.sessionReady && !state.equalizer) "Equalizer is unavailable on this output." else "Low end                                    High end", Modifier.fillMaxWidth().padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
        } }
        item { EffectStrength("Bass boost", "Add weight to the low end", Icons.Rounded.SurroundSound, config.bass, config.enabled && (!state.sessionReady || state.bass), state.sessionReady && !state.bass) { onUpdate(config.copy(bass = it)) } }
        item { EffectStrength("Headphone space", "A wider stereo sound · supported outputs only", Icons.Rounded.Headphones, config.spatial, config.enabled && (!state.sessionReady || state.spatial), state.sessionReady && !state.spatial) { onUpdate(config.copy(spatial = it)) } }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.VerifiedUser, null, tint = MaterialTheme.colorScheme.secondary)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("Headroom protection", fontWeight = FontWeight.Medium); Text("Lower playback level when boosting EQ or bass.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Switch(config.headroom, onCheckedChange = { onUpdate(config.copy(headroom = it)) }, modifier = Modifier.semantics { contentDescription = "Headroom protection" })
                }
            }
        }
        item { Text("Changes apply to Pulsify playback and stay active in the background. Available effects and band limits depend on your device and audio output.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable private fun EqualizerFader(label: String, value: Float, enabled: Boolean, modifier: Modifier, minimum: Float, maximum: Float, onChange: (Float) -> Unit) {
    val change by rememberUpdatedState(onChange)
    val low = minimum.coerceAtMost(-.1f); val high = maximum.coerceAtLeast(.1f)
    val color = if(enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val track = MaterialTheme.colorScheme.outline.copy(alpha = .55f)
    val thumb = MaterialTheme.colorScheme.onSurface
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${if(value > 0) "+" else ""}${value.roundToInt()}", style = MaterialTheme.typography.labelMedium, color = color)
        Canvas(Modifier.weight(1f).fillMaxWidth().semantics {
            contentDescription = "$label Hz equalizer band"
            stateDescription = "${value.roundToInt()} decibels"
            progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(low,high), low..high)
            if(!enabled) disabled()
            setProgress { if(enabled) { change(it.coerceIn(low,high)); true } else false }
        }.onKeyEvent { event ->
            if(enabled && event.type == KeyEventType.KeyDown && event.key in listOf(Key.DirectionUp, Key.DirectionDown)) {
                change((value + if(event.key == Key.DirectionUp) 1 else -1).coerceIn(low,high)); true
            } else false
        }.focusable(enabled).pointerInput(enabled, low, high) {
            if(enabled) awaitEachGesture {
                val down = awaitFirstDown()
                fun update(y: Float) { val fraction = ((y - 16.dp.toPx()) / (size.height - 32.dp.toPx()).coerceAtLeast(1f)).coerceIn(0f,1f); change(((1f - fraction) * (high - low) + low).roundToInt().toFloat()) }
                down.consume(); update(down.position.y)
                do { val event = awaitPointerEvent(); val pointer = event.changes.firstOrNull { it.id == down.id } ?: break; update(pointer.position.y); pointer.consume() } while(pointer.pressed)
            }
        }) {
            val x = size.width / 2; val inset = 16.dp.toPx(); val y = inset + (high - value.coerceIn(low,high)) / (high - low) * (size.height - 2 * inset)
            drawLine(track, Offset(x,inset), Offset(x,size.height-inset), strokeWidth = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(color, Offset(x,inset + high / (high - low) * (size.height - 2 * inset)), Offset(x,y), strokeWidth = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(track, Offset(x-7.dp.toPx(),size.height/2), Offset(x+7.dp.toPx(),size.height/2), strokeWidth = 1.dp.toPx())
            drawCircle(color, 11.dp.toPx(), Offset(x,y)); drawCircle(thumb, 4.dp.toPx(), Offset(x,y))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Hz", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f))
    }
}
@Composable private fun EffectStrength(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, value: Int, enabled: Boolean, unavailable: Boolean, onChange: (Int) -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(if(unavailable) "Unavailable on this output" else description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text("${value / 10}%", style = MaterialTheme.typography.labelMedium) }
            Slider(value.toFloat(), { onChange(it.roundToInt()) }, valueRange = 0f..1000f, enabled = enabled, modifier = Modifier.semantics { contentDescription = title })
        }
    }
}
