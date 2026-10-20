@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.pulsify.music.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable fun NowPlayingScreen(vm: MusicViewModel, onBack: () -> Unit, onQueue: () -> Unit, onLyrics: () -> Unit, onMore: () -> Unit, onEffects: () -> Unit) {
    val state by vm.playback.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    var tools by remember { mutableStateOf(false) }
    var sleep by remember { mutableStateOf(false) }
    val ambient by animateColorAsState(artColor(state.item?.mediaMetadata?.title.toString()).copy(alpha = .55f), tween(800), label = "Player atmosphere")
    val base = MaterialTheme.colorScheme.background
    if(state.item == null) {
        Column { ActionIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack); EmptyState("Your soundtrack is waiting", "Choose a song from your library.") }; return
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(ambient, base, base)))) {
        val landscape = maxWidth > 600.dp && maxWidth > maxHeight
        val artSize = if(landscape) minOf(maxWidth * .4f, maxHeight - 100.dp, 380.dp).coerceAtLeast(100.dp) else minOf(maxWidth - 56.dp, maxHeight * .4f, 430.dp).coerceAtLeast(110.dp)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ActionIcon(Icons.Rounded.KeyboardArrowDown, "Close now playing", onClick = onBack)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYING FROM YOUR COLLECTION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                    Text(state.item?.mediaMetadata?.albumTitle?.toString().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                }
                ActionIcon(Icons.Rounded.MoreHoriz, "Track options", onClick = onMore)
            }
            if(landscape) Row(Modifier.weight(1f).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { PlayerArtwork(state, artSize, vm::next, vm::previous) }
                Column(Modifier.weight(1.15f).verticalScroll(rememberScrollState()).padding(start = 28.dp, bottom = 20.dp)) {
                    PlayerControls(state, current?.favorite == true, { current?.let { vm.favorite(it.song) } }, vm, onQueue, onLyrics, onEffects, { tools = true }, { sleep = true })
                }
            } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 14.dp, bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PlayerArtwork(state, artSize, vm::next, vm::previous)
                Spacer(Modifier.height(30.dp))
                Box(Modifier.widthIn(max = 560.dp).fillMaxWidth()) { PlayerControls(state, current?.favorite == true, { current?.let { vm.favorite(it.song) } }, vm, onQueue, onLyrics, onEffects, { tools = true }, { sleep = true }) }
            }
        }
    }
    if(tools) ModalBottomSheet(onDismissRequest = { tools = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Make every second yours", style = MaterialTheme.typography.headlineSmall)
            Text("Playback speed", fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(.5f,.75f,1f,1.25f,1.5f,2f).forEach { speed -> FilterChip(state.speed == speed, onClick = { vm.speed(speed) }, label = { Text("$speed×") }) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Player volume", fontWeight = FontWeight.SemiBold); Text("${(state.volume * 100).roundToInt()}%", color = MaterialTheme.colorScheme.primary) }
            var volume by remember(state.volume) { mutableFloatStateOf(state.volume) }
            Slider(volume, { volume = it; vm.volume(it) }, valueRange = 0f..1f, modifier = Modifier.semantics { contentDescription = "Pulsify playback volume" })
            val settings by vm.settings.collectAsStateWithLifecycle()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { vm.skip(-1) }) { Icon(Icons.Rounded.Replay, null); Spacer(Modifier.width(8.dp)); Text("−${settings.skip}s") }
                TextButton(onClick = { vm.skip(1) }) { Icon(Icons.Rounded.FastForward, null); Spacer(Modifier.width(8.dp)); Text("+${settings.skip}s") }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("A–B repeat", style = MaterialTheme.typography.titleLarge)
                if(state.loopA >= 0) TextButton(onClick = { vm.markLoop(true) }) { Text("Clear loop") }
            }
            Text(when { state.loopB >= 0 -> "Looping ${time(state.loopA)} → ${time(state.loopB)}"; state.loopA >= 0 -> "A is set at ${time(state.loopA)}. Seek ahead and set B."; else -> "Replay a favorite verse, practice a passage, or stay in the moment." }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { vm.markLoop() }, enabled = state.duration > 0 && (state.loopA < 0 || state.loopB >= 0 || state.position >= state.loopA + 1000), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Loop, null); Spacer(Modifier.width(8.dp)); Text(when { state.loopB >= 0 -> "Set a new A point"; state.loopA >= 0 -> "Set B at ${time(state.position)}"; else -> "Set A at ${time(state.position)}" })
            }
            TextButton(onClick = { tools = false; sleep = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Bedtime, null); Spacer(Modifier.width(8.dp)); Text("Sleep timer · ${state.sleep}") }
        }
    }
    if(sleep) SleepSheet(state, vm) { sleep = false }
}
@Composable private fun PlayerArtwork(state: Playback, size: androidx.compose.ui.unit.Dp, onNext: () -> Unit, onPrevious: () -> Unit) {
    var drag by remember { mutableFloatStateOf(0f) }
    val shift by animateFloatAsState(drag * .3f, spring(stiffness = 500f), label = "Artwork gesture")
    AnimatedContent(state.item?.mediaMetadata, modifier = Modifier.size(size).graphicsLayer { translationX = shift; alpha = (1f - abs(shift) / 600f).coerceIn(.5f,1f) }.pointerInput(Unit) {
        detectHorizontalDragGestures(onDragStart = { drag = 0f }, onDragEnd = { if(drag < -90f) onNext() else if(drag > 90f) onPrevious(); drag = 0f }, onDragCancel = { drag = 0f }) { change, amount -> change.consume(); drag += amount }
    }, transitionSpec = {
        (fadeIn(tween(430)) + slideInHorizontally(tween(480, easing = FastOutSlowInEasing)) { it / 3 } + scaleIn(tween(480), initialScale = .92f))
            .togetherWith(fadeOut(tween(220)) + slideOutHorizontally(tween(350)) { -it / 4 })
    }, label = "Track artwork transition") { metadata ->
        Artwork(metadata?.artworkUri?.path, metadata?.title.toString(), Modifier.fillMaxSize().shadow(26.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)))
    }
}
@Composable private fun PlayerControls(state: Playback, favorite: Boolean, onFavorite: () -> Unit, vm: MusicViewModel,
    onQueue: () -> Unit, onLyrics: () -> Unit, onEffects: () -> Unit, onTools: () -> Unit, onSleep: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(state.item?.mediaMetadata, modifier = Modifier.weight(1f), transitionSpec = {
                (fadeIn(tween(300)) + slideInVertically(tween(350)) { it / 2 }).togetherWith(fadeOut(tween(150)))
            }, label = "Track title transition") { metadata -> Column {
                Text(metadata?.title.toString(), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.headlineMedium)
                Text(metadata?.artist.toString(), Modifier.padding(top = 7.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            } }
            IconToggleButton(favorite, onCheckedChange = { onFavorite() }) {
                AnimatedContent(favorite, transitionSpec = { (scaleIn(spring(dampingRatio = .55f), initialScale = .4f) + fadeIn()).togetherWith(scaleOut() + fadeOut()) }, label = "Favorite feedback") { liked -> Icon(if(liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Toggle favorite", tint = if(liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        var dragging by remember(state.item?.mediaId) { mutableStateOf<Float?>(null) }
        Slider(dragging ?: state.position.toFloat().coerceAtMost(state.duration.toFloat()), onValueChange = { dragging = it },
            valueRange = 0f..state.duration.coerceAtLeast(1).toFloat(), onValueChangeFinished = { dragging?.let { vm.seek(it.toLong()) }; dragging = null }, enabled = state.duration > 0, modifier = Modifier.padding(top = 20.dp).semantics { contentDescription = "Seek in current song" })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(time(dragging?.toLong() ?: state.position), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("−${time(state.duration - (dragging?.toLong() ?: state.position))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconToggleButton(state.shuffle, onCheckedChange = { vm.shuffle() }) { Icon(Icons.Rounded.Shuffle, if(state.shuffle) "Shuffle on" else "Shuffle off", tint = if(state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
            ActionIcon(Icons.Rounded.SkipPrevious, "Previous song", onClick = vm::previous)
            FilledIconButton(onClick = vm::toggle, modifier = Modifier.size(78.dp), shape = CircleShape, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Acid, contentColor = Color(0xFF19210F))) {
                AnimatedContent(state.playing, transitionSpec = { (fadeIn(tween(180)) + scaleIn(tween(240), initialScale = .65f)).togetherWith(fadeOut(tween(100)) + scaleOut(targetScale = .65f)) }, label = "Play pause feedback") { playing -> Icon(if(playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if(playing) "Pause" else "Play", Modifier.size(39.dp)) }
            }
            ActionIcon(Icons.Rounded.SkipNext, "Next song", onClick = vm::next)
            ActionIcon(if(state.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                "Repeat ${when(state.repeat) { Player.REPEAT_MODE_ONE -> "one"; Player.REPEAT_MODE_ALL -> "all"; else -> "off" }}", tint = if(state.repeat != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, onClick = vm::repeat)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = onTools) { Icon(Icons.Rounded.Speed, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("${state.speed}×") }
            TextButton(onClick = onSleep) { Icon(Icons.Rounded.Bedtime, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(if(state.sleep == "Off") "Sleep" else state.sleep, maxLines = 1) }
            TextButton(onClick = onTools) { Icon(Icons.Rounded.Loop, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(if(state.loopB >= 0) "A–B on" else "A–B") }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlayerPanel("Up next", Icons.AutoMirrored.Rounded.QueueMusic, Modifier.weight(1f), onQueue)
            PlayerPanel("Lyrics", Icons.Rounded.Lyrics, Modifier.weight(1f), onLyrics)
            PlayerPanel("Sound", Icons.Rounded.Equalizer, Modifier.weight(1f), onEffects)
        }
        Text("Swipe the artwork to change tracks", Modifier.fillMaxWidth().padding(top = 16.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
@Composable private fun PlayerPanel(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick, modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .06f)) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) { Icon(icon, null, Modifier.size(21.dp)); Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium) }
    }
}
@Composable private fun SleepSheet(state: Playback, vm: MusicViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Rounded.Bedtime, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Drift off to your favorites.", style = MaterialTheme.typography.headlineSmall)
            Text("Sleep timer · ${state.sleep}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(listOf(5,10,15), listOf(30,45,60)).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { minutes -> FilledTonalButton(onClick = { vm.sleep(minutes); onDismiss() }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 14.dp)) { Text("${minutes}m") } } } }
            MenuAction("End of this song", Icons.Rounded.MusicNote) { vm.sleep(tracks = 1); onDismiss() }
            MenuAction("End of queue · repeat must be off", Icons.AutoMirrored.Rounded.QueueMusic) { vm.sleep(end = true); onDismiss() }
            Text("Or after a few songs", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(2,3,5,10).forEach { songs -> AssistChip(onClick = { vm.sleep(tracks = songs); onDismiss() }, label = { Text("$songs songs") }) } }
            if(state.sleep != "Off") OutlinedButton(onClick = { vm.sleep(); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Cancel timer") }
        }
    }
}
@Composable fun QueueScreen(vm: MusicViewModel, onBack: () -> Unit) {
    val state by vm.playback.collectAsStateWithLifecycle()
    var clear by remember { mutableStateOf(false) }
    val keys = remember(state.queue) { val occurrences = mutableMapOf<String,Int>(); state.queue.map { item -> val n = occurrences.getOrDefault(item.mediaId, 0); occurrences[item.mediaId] = n + 1; "${item.mediaId}#$n" } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
            Column(Modifier.weight(1f)) { Text("UP NEXT", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall); Text("Keep it going", style = MaterialTheme.typography.headlineMedium) }
            var menu by remember { mutableStateOf(false) }
            Box { ActionIcon(Icons.Rounded.MoreHoriz, "Queue options") { menu = true }; DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("Remove already played") }, enabled = state.index > 0, onClick = { vm.clearPlayedQueue(); menu = false })
                DropdownMenuItem(text = { Text("Clear queue") }, enabled = state.queue.isNotEmpty(), onClick = { clear = true; menu = false })
            } }
        }
        Text("${state.queue.size} songs · Swipe left to remove", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if(state.queue.isEmpty()) EmptyState("Leave room for the next song", "Play something or add a few favorites from your library.", Icons.AutoMirrored.Rounded.QueueMusic)
        LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
            itemsIndexed(state.queue, key = { index,_ -> keys[index] }) { index,item ->
                val currentIndex by rememberUpdatedState(index)
                val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { value -> if(value == SwipeToDismissBoxValue.EndToStart) { vm.removeQueue(currentIndex); false } else false })
                Column(Modifier.animateItem()) {
                    if(index == state.index) Text("NOW PLAYING", Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                    SwipeToDismissBox(dismiss, backgroundContent = { if(dismiss.dismissDirection != SwipeToDismissBoxValue.Settled) Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(18.dp), contentAlignment = Alignment.CenterEnd) { Icon(Icons.Rounded.DeleteOutline, "Remove", tint = MaterialTheme.colorScheme.onErrorContainer) } }, enableDismissFromStartToEnd = false) {
                        Surface(shape = RoundedCornerShape(16.dp), color = if(index == state.index) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f) else MaterialTheme.colorScheme.surface) {
                            Row(Modifier.fillMaxWidth().clickable { vm.jumpQueue(index) }.padding(vertical = 10.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Artwork(item.mediaMetadata.artworkUri?.path, item.mediaMetadata.title.toString(), Modifier.size(52.dp))
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(item.mediaMetadata.title.toString(), maxLines = 1, overflow = TextOverflow.Ellipsis, color = if(index == state.index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall)
                                    Text(item.mediaMetadata.artist.toString(), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if(dismiss.dismissDirection == SwipeToDismissBoxValue.Settled) ActionIcon(Icons.Rounded.Close, "Remove from queue") { vm.removeQueue(index) } else Spacer(Modifier.size(48.dp))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { vm.moveQueue(index,index - 1) }, enabled = index > 0) { Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(15.dp)); Text("Up", style = MaterialTheme.typography.labelSmall) }
                        TextButton(onClick = { vm.moveQueue(index,index + 1) }, enabled = index < state.queue.lastIndex) { Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(15.dp)); Text("Down", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
    }
    if(clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("Clear the queue?") }, text = { Text("Playback will stop. Your music and playlists will stay safe.") }, confirmButton = { TextButton(onClick = { vm.clearQueue(); clear = false }) { Text("Clear queue") } }, dismissButton = { TextButton(onClick = { clear = false }) { Text("Cancel") } })
}
