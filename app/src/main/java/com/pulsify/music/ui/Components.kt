package com.pulsify.music.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.pulsify.music.data.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

fun time(ms: Long): String { val seconds = ms.coerceAtLeast(0) / 1000; return "%d:%02d".format(seconds / 60, seconds % 60) }
@Composable fun Artwork(art: String?, title: String, modifier: Modifier = Modifier) {
    val colors = listOf(Color(0xFF725596), Color(0xFF3B707D), Color(0xFF955F71), Color(0xFF6D7051))
    val color = colors[(title.hashCode().toLong().let { kotlin.math.abs(it) } % colors.size).toInt()]
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(color, color.copy(red = color.red * .4f, green = color.green * .4f, blue = color.blue * .4f)))), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MusicNote, null, Modifier.fillMaxSize(.35f), tint = Color.White.copy(alpha = .55f))
        if(art != null) AsyncImage(model = File(art), contentDescription = "Artwork for $title", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
@Composable fun ActionIcon(icon: ImageVector, description: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Icon(icon, description, tint = tint) }
}
@Composable fun SectionTitle(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if(action != null) TextButton(onClick = onClick) { Text(action); Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp)) }
    }
}
@Composable fun EmptyState(title: String, subtitle: String, icon: ImageVector = Icons.Rounded.LibraryMusic, action: String? = null, onClick: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) { Icon(icon, null, Modifier.padding(24.dp).size(40.dp), tint = MaterialTheme.colorScheme.primary) }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if(action != null) FilledTonalButton(onClick = onClick) { Text(action) }
    }
}
@Composable fun SongRow(song: Song, favorite: Boolean, active: Boolean = false, onClick: () -> Unit, onMore: () -> Unit, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(song.artwork, song.title, Modifier.size(54.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, color = if(active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(favorite) Icon(Icons.Rounded.Favorite, "Favorite", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        Text(trailing ?: time(song.duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
        ActionIcon(Icons.Rounded.MoreHoriz, "Options for ${song.title}", onClick = onMore)
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable fun KeyboardSafeField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, onDone: () -> Unit = {}) {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true,
        modifier = modifier.fillMaxWidth().bringIntoViewRequester(requester).onFocusChanged {
            if(it.isFocused) scope.launch { delay(300); requester.bringIntoView() }
        }, shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); onDone() }))
}
@Composable fun NameDialog(title: String, initial: String = "", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(20.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 480.dp).fillMaxWidth(), shape = RoundedCornerShape(28.dp), tonalElevation = 8.dp) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    KeyboardSafeField(name, { name = it.take(80) }, "Playlist name", onDone = { if(name.isNotBlank()) { onSave(name); onDismiss() } })
                    Row(Modifier.align(Alignment.End)) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Button(onClick = { onSave(name); onDismiss() }, enabled = name.isNotBlank()) { Text("Save") }
                    }
                }
            }
        }
    }
}
@Composable fun MiniPlayer(state: Playback, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
    val progress by animateFloatAsState(if(state.duration > 0) (state.position.toFloat() / state.duration).coerceIn(0f,1f) else 0f, tween(900), label = "Playback progress")
    Surface(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onOpen), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(20.dp)) {
        Column {
            Row(Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(state.item?.mediaMetadata?.artworkUri?.path, state.item?.mediaMetadata?.title.toString(), Modifier.size(44.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(state.item?.mediaMetadata?.title?.toString().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(state.item?.mediaMetadata?.artist?.toString().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ActionIcon(if(state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if(state.playing) "Pause" else "Play", onClick = onToggle)
                ActionIcon(Icons.Rounded.SkipNext, "Next song", onClick = onNext)
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp), color = MaterialTheme.colorScheme.primary, trackColor = Color.Transparent, drawStopIndicator = {})
        }
    }
}
