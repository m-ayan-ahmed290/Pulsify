package com.pulsify.music.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.sp
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
fun artColor(title: String): Color = listOf(Color(0xFF6653A5), Color(0xFF256C70), Color(0xFFA75B55), Color(0xFF536D46), Color(0xFF3764A2))[(kotlin.math.abs(title.hashCode().toLong()) % 5).toInt()]
@Composable fun Artwork(art: String?, title: String, modifier: Modifier = Modifier) {
    val color = artColor(title)
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(color.copy(red = (color.red + .2f).coerceAtMost(1f)), color, Color(0xFF171526)))), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = androidx.compose.ui.geometry.Offset(size.width * .65f, size.height * .4f)
            for(i in 1..6) drawCircle(Color.White.copy(alpha = .07f), size.minDimension * (.12f + i * .085f), center, style = Stroke(size.minDimension * .006f))
            drawCircle(Color.White.copy(alpha = .08f), size.minDimension * .12f, center)
        }
        Text(title.firstOrNull()?.uppercaseChar()?.toString() ?: "♪", color = Color.White.copy(alpha = .82f), fontSize = 26.sp, fontWeight = FontWeight.Black)
        if(art != null) AsyncImage(model = File(art), contentDescription = "Artwork for $title", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
@Composable fun Entrance(index: Int = 0, content: @Composable () -> Unit) {
    var entered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by animateFloatAsState(if(entered) 1f else 0f, tween(500, index.coerceIn(0,6) * 45, FastOutSlowInEasing), label = "Content entrance")
    Box(Modifier.graphicsLayer { alpha = progress; translationY = (1f - progress) * 48f }) { content() }
}
@Composable fun ActionIcon(icon: ImageVector, description: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Icon(icon, description, tint = tint) }
}
@Composable fun SectionTitle(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if(action != null) TextButton(onClick = onClick) { Text(action, style = MaterialTheme.typography.labelMedium); Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp)) }
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
@OptIn(ExperimentalFoundationApi::class)
@Composable fun SongRow(song: Song, favorite: Boolean, active: Boolean = false, onClick: () -> Unit, onMore: () -> Unit, trailing: String? = null, onLongPress: (() -> Unit)? = null, selected: Boolean? = null) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if(selected == true || active) MaterialTheme.colorScheme.primary.copy(alpha = .07f) else Color.Transparent).combinedClickable(onClick = onClick, onLongClick = onLongPress).padding(vertical = 9.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if(selected != null) Checkbox(selected, onCheckedChange = { onClick() }, modifier = Modifier.size(48.dp))
        Artwork(song.artwork, song.title, Modifier.size(50.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, color = if(active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(active) Icon(Icons.Rounded.GraphicEq, "Current track", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        else if(favorite) Icon(Icons.Rounded.Favorite, "Favorite", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        Text(trailing ?: time(song.duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
        ActionIcon(Icons.Rounded.MoreHoriz, "Options for ${song.title}", onClick = onMore)
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable fun KeyboardSafeField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, onDone: () -> Unit = {}, leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, leadingIcon = leading, trailingIcon = trailing,
        modifier = modifier.fillMaxWidth().bringIntoViewRequester(requester).onFocusChanged {
            if(it.isFocused) scope.launch { delay(300); requester.bringIntoView() }
        }, shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); onDone() }))
}
@Composable fun NameDialog(title: String, initial: String = "", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
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
@Composable fun MiniPlayer(state: Playback, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit = {}) {
    val progress by animateFloatAsState(if(state.duration > 0) (state.position.toFloat() / state.duration).coerceIn(0f,1f) else 0f, tween(900, easing = LinearEasing), label = "Playback progress")
    var swipe by remember { mutableStateOf(0f) }
    val color = artColor(state.item?.mediaMetadata?.title.toString())
    Surface(Modifier.padding(8.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onOpen).pointerInput(Unit) {
        detectHorizontalDragGestures(onDragStart = { swipe = 0f }, onDragEnd = { if(swipe < -80f) onNext() else if(swipe > 80f) onPrevious(); swipe = 0f }) { change, amount -> change.consume(); swipe += amount }
    }, color = color.copy(alpha = .45f), shape = RoundedCornerShape(18.dp)) {
        Column {
            Row(Modifier.padding(start = 10.dp, top = 9.dp, bottom = 9.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(state.item?.mediaMetadata?.artworkUri?.path, state.item?.mediaMetadata?.title.toString(), Modifier.size(44.dp))
                AnimatedContent(targetState = state.item?.mediaId, modifier = Modifier.weight(1f), transitionSpec = {
                    (fadeIn(tween(260)) + slideInVertically(tween(320)) { it / 2 }).togetherWith(fadeOut(tween(160)))
                }, label = "Mini-player track") { id ->
                    val item = state.queue.firstOrNull { it.mediaId == id } ?: state.item
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        Text(item?.mediaMetadata?.title?.toString().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text(item?.mediaMetadata?.artist?.toString().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
                    }
                }
                ActionIcon(if(state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if(state.playing) "Pause" else "Play", onClick = onToggle)
                ActionIcon(Icons.Rounded.SkipNext, "Next song", onClick = onNext)
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .8f), trackColor = Color.Transparent, drawStopIndicator = {})
        }
    }
}
