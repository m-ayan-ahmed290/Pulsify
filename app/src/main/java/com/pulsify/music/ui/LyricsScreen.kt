package com.pulsify.music.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsify.music.domain.*

@Composable fun LyricsContent(document: LyricsDocument?, position: Long, title: String, onAttach: () -> Unit,
    onSeek: (Long) -> Unit, onDelay: (Long) -> Unit, onRemove: () -> Unit) {
    val parsed = remember(document?.text) { parseLyrics(document?.text.orEmpty()) }
    val delay = document?.delayMs ?: 0
    val active = parsed.activeIndex(position, delay)
    val list = rememberLazyListState()
    var follow by rememberSaveable(document?.songId) { mutableStateOf(true) }
    var timing by rememberSaveable { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    LaunchedEffect(list, document?.songId) { list.interactionSource.interactions.collect { if(it is DragInteraction.Start) follow = false } }
    LaunchedEffect(active, follow, document?.text) { if(follow && active >= 0) list.animateScrollToItem((active - 2).coerceAtLeast(0)) }
    Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if(parsed.synced) "LIVE LYRICS" else "YOUR WORDS", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                Text("Lyrics", style = MaterialTheme.typography.headlineMedium)
            }
            TextButton(onClick = onAttach) { Text(if(document == null) "Attach file" else "Replace") }
            if(document != null) ActionIcon(Icons.Rounded.DeleteOutline, "Remove attached lyrics") { remove = true }
        }
        Text(title, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if(parsed.synced) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(follow, onClick = { follow = !follow }, label = { Text(if(follow) "Following" else "Follow song") }, leadingIcon = { Icon(Icons.Rounded.MyLocation, null, Modifier.size(16.dp)) })
                TextButton(onClick = { timing = !timing }) { Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Timing") }
            }
            if(timing) Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(10.dp)) {
                    Text("Adjust this song · + delays lyrics", style = MaterialTheme.typography.labelSmall)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { onDelay(delay - 250) }, enabled = delay > -30_000) { Text("−0.25s") }
                        TextButton(onClick = { onDelay(0) }) { Text("${if(delay > 0) "+" else ""}${delay} ms · Reset") }
                        TextButton(onClick = { onDelay(delay + 250) }, enabled = delay < 30_000) { Text("+0.25s") }
                    }
                }
            }
            Text("Tap a line to play from there", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(parsed.lines, key = { _, line -> line.timeMs }) { index,line ->
                    val selected = index == active
                    val color by animateColorAsState(if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, label = "Active lyric")
                    Surface(shape = RoundedCornerShape(16.dp), color = if(selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .4f) else MaterialTheme.colorScheme.surface) {
                        Text(line.text.ifBlank { "♪ Instrumental" }, Modifier.fillMaxWidth().clickable(onClickLabel = "Play from lyric") { follow = true; onSeek(parsed.seekPosition(index, delay)) }
                            .semantics { this.selected = selected }.padding(horizontal = 14.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.headlineSmall, fontWeight = if(selected) FontWeight.Bold else FontWeight.Medium, color = color)
                    }
                }
            }
        } else {
            Text(if(document == null) "Attach a local .txt or .lrc file. Timestamped LRC lyrics follow the song and support tap-to-seek."
                else parsed.plainText + "\n\nPlain text lyrics · Attach a timestamped LRC file to follow playback.",
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 24.dp), style = MaterialTheme.typography.titleLarge)
        }
    }
    if(remove) AlertDialog(onDismissRequest = { remove = false }, title = { Text("Remove attached lyrics?") }, text = { Text("The original file stays on your device.") },
        confirmButton = { TextButton(onClick = { remove = false; onRemove() }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { remove = false }) { Text("Cancel") } })
}
