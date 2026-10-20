package com.pulsify.music.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player

@Composable fun NowPlayingScreen(vm: MusicViewModel, onBack: () -> Unit, onQueue: () -> Unit, onLyrics: () -> Unit, onMore: () -> Unit) {
    val state by vm.playback.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    var sleep by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    if(state.item == null) {
        Column { ActionIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack); EmptyState("Nothing playing yet", "Choose a song from your library.") }; return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val artSize = minOf(maxWidth - 64.dp, maxHeight * .43f, 380.dp).coerceAtLeast(100.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ActionIcon(Icons.Rounded.KeyboardArrowDown, "Close now playing", onClick = onBack)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NOW PLAYING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(state.item?.mediaMetadata?.albumTitle?.toString().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                }
                ActionIcon(Icons.Rounded.MoreHoriz, "Track options", onClick = onMore)
            }
            Spacer(Modifier.height(16.dp))
            AnimatedContent(targetState = state.item?.mediaId, transitionSpec = {
                (fadeIn(tween(320)) + scaleIn(tween(320), initialScale = .96f)).togetherWith(fadeOut(tween(180)))
            }, label = "Album artwork") { id ->
                // Resolve the outgoing artwork separately so the transition keeps its own content.
                val item = state.queue.firstOrNull { it.mediaId == id } ?: state.item
                Artwork(item?.mediaMetadata?.artworkUri?.path, item?.mediaMetadata?.title.toString(), Modifier.size(artSize))
            }
            Spacer(Modifier.height(28.dp))
            Row(Modifier.widthIn(max = 540.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.item?.mediaMetadata?.title.toString(), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(state.item?.mediaMetadata?.artist.toString(), Modifier.padding(top = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                }
                ActionIcon(if(current?.favorite == true) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Toggle favorite", tint = MaterialTheme.colorScheme.primary) { current?.let { vm.favorite(it.song) } }
            }
            Column(Modifier.widthIn(max = 540.dp).fillMaxWidth().padding(top = 20.dp)) {
                Slider(value = dragging ?: state.position.toFloat().coerceAtMost(state.duration.toFloat()), onValueChange = { dragging = it },
                    valueRange = 0f..state.duration.coerceAtLeast(1).toFloat(), onValueChangeFinished = { dragging?.let { vm.seek(it.toLong()) }; dragging = null }, enabled = state.duration > 0)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(time(dragging?.toLong() ?: state.position), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("−${time(state.duration - (dragging?.toLong() ?: state.position))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                ActionIcon(Icons.Rounded.Shuffle, if(state.shuffle) "Disable shuffle" else "Enable shuffle", tint = if(state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, onClick = vm::shuffle)
                ActionIcon(Icons.Rounded.SkipPrevious, "Previous song", onClick = vm::previous)
                FilledIconButton(onClick = vm::toggle, modifier = Modifier.size(76.dp), shape = CircleShape) {
                    Icon(if(state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if(state.playing) "Pause" else "Play", Modifier.size(38.dp))
                }
                ActionIcon(Icons.Rounded.SkipNext, "Next song", onClick = vm::next)
                ActionIcon(if(state.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat: ${when(state.repeat) { 1 -> "one"; 2 -> "all"; else -> "off" }}", tint = if(state.repeat != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, onClick = vm::repeat)
            }
            Row(Modifier.widthIn(max = 460.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { speed = true }) { Text("${state.speed}×") }
                ActionIcon(Icons.Rounded.Replay10, "Rewind 10 seconds") { vm.skip(-1) }
                ActionIcon(Icons.Rounded.Forward10, "Forward 10 seconds") { vm.skip(1) }
                ActionIcon(Icons.Rounded.Timer, "Sleep timer: ${state.sleep}", tint = if(state.sleep != "Off") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) { sleep = true }
            }
            Row(Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onLyrics) { Icon(Icons.Rounded.Lyrics, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Lyrics") }
                TextButton(onClick = onQueue) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Queue · ${state.queue.size}") }
            }
            if(state.sleep != "Off") Text("Sleep timer · ${state.sleep}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
    if(speed) AlertDialog(onDismissRequest = { speed = false }, title = { Text("Playback speed") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) { listOf(.5f,.75f,1f,1.25f,1.5f,2f).forEach { value ->
            TextButton(onClick = { vm.speed(value); speed = false }, Modifier.fillMaxWidth()) { Text("$value×" + if(state.speed == value) " ✓" else "") }
        } }
    }, confirmButton = { TextButton(onClick = { speed = false }) { Text("Close") } })
    if(sleep) AlertDialog(onDismissRequest = { sleep = false }, title = { Text("Drift off to your music") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Text("Timer: ${state.sleep}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(5,10,15,30,45,60).forEach { minutes -> TextButton(onClick = { vm.sleep(minutes); sleep = false }, Modifier.fillMaxWidth()) { Text("$minutes minutes") } }
            TextButton(onClick = { vm.sleep(tracks = 1); sleep = false }, Modifier.fillMaxWidth()) { Text("End of current song") }
            TextButton(onClick = { vm.sleep(end = true); sleep = false }, Modifier.fillMaxWidth()) { Text("End of queue (repeat must be off)") }
            listOf(2,3,5,10).forEach { songs -> TextButton(onClick = { vm.sleep(tracks = songs); sleep = false }, Modifier.fillMaxWidth()) { Text("After $songs songs") } }
        }
    }, confirmButton = { TextButton(onClick = { vm.sleep(); sleep = false }) { Text("Cancel timer") } }, dismissButton = { TextButton(onClick = { sleep = false }) { Text("Close") } })
}
@Composable fun QueueScreen(vm: MusicViewModel, onBack: () -> Unit) {
    val state by vm.playback.collectAsStateWithLifecycle()
    var clear by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
            Column(Modifier.weight(1f)) { Text("Your queue", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("${state.queue.size} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            TextButton(onClick = { clear = true }, enabled = state.queue.isNotEmpty()) { Text("Clear") }
        }
        if(state.queue.isEmpty()) EmptyState("A clean slate", "Play a song or add something from your library.", Icons.AutoMirrored.Rounded.QueueMusic)
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            itemsIndexed(state.queue, key = { i,item -> "$i-${item.mediaId}" }) { index, item ->
                Column(Modifier.animateItem()) {
                    Row(Modifier.fillMaxWidth().clickable { vm.jumpQueue(index) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(item.mediaMetadata.artworkUri?.path, item.mediaMetadata.title.toString(), Modifier.size(48.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(item.mediaMetadata.title.toString(), maxLines = 1, overflow = TextOverflow.Ellipsis, color = if(index == state.index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                            Text(if(index == state.index) "Now playing · ${item.mediaMetadata.artist}" else item.mediaMetadata.artist.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        ActionIcon(Icons.Rounded.Close, "Remove from queue") { vm.removeQueue(index) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { vm.moveQueue(index, index - 1) }, enabled = index > 0) { Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(16.dp)); Text("Move up") }
                        TextButton(onClick = { vm.moveQueue(index, index + 1) }, enabled = index < state.queue.lastIndex) { Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(16.dp)); Text("Move down") }
                    }
                }
            }
        }
    }
    if(clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("Clear the queue?") }, text = { Text("Playback will stop. Your playlists and music files will be kept.") },
        confirmButton = { TextButton(onClick = { vm.clearQueue(); clear = false }) { Text("Clear queue") } }, dismissButton = { TextButton(onClick = { clear = false }) { Text("Cancel") } })
}
