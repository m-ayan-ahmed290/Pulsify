package com.pulsify.music.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulsify.music.R
import com.pulsify.music.data.Track
import java.time.LocalTime

@Composable fun HomeScreen(vm: MusicViewModel, onLibrary: (String) -> Unit, onSettings: () -> Unit,
    onMore: (Track) -> Unit, onNow: () -> Unit, onEffects: () -> Unit, onAddMusic: () -> Unit) {
    val count by vm.count.collectAsStateWithLifecycle()
    val fresh by vm.home.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val liked by vm.liked.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val greeting = when(LocalTime.now().hour) { in 5..11 -> "GOOD MORNING"; in 12..17 -> "GOOD AFTERNOON"; else -> "GOOD EVENING" }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item("header") { Entrance {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Violet) { Image(painterResource(R.mipmap.pulsify_mark), "Pulsify", Modifier.size(43.dp)) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(greeting, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("pulsify", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                }
                ActionIcon(Icons.Rounded.GraphicEq, "Open Sound Studio", onClick = onEffects)
                ActionIcon(Icons.Rounded.Tune, "Open settings", onClick = onSettings)
            }
        } }
        item("filters") { Entrance(1) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(true, onClick = { onLibrary("Songs") }, label = { Text("All music") }, leadingIcon = { Icon(Icons.Rounded.MusicNote, null, Modifier.size(16.dp)) })
                FilterChip(false, onClick = { onLibrary("Favorites") }, label = { Text("Liked songs") })
                FilterChip(false, onClick = { onLibrary("Recently played") }, label = { Text("On repeat") })
                FilterChip(false, onClick = { onLibrary("Albums") }, label = { Text("Albums") })
            }
        } }
        item("mix") { Entrance(2) {
            Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF35423B), Color(0xFF233533), Color(0xFF151E29))))) {
                Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Text("YOUR OWN FREQUENCY", color = Acid, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text(if(count > 0) "Your music.\nA fresh mix." else "Good music.\nCloser to you.", style = MaterialTheme.typography.displaySmall, color = Color.White)
                        Text(if(count > 0) "Favorites meet rediscoveries.\nA mix from your collection." else "Bring your collection.\nWe’ll bring the good vibes.", color = Color(0xFFC0D1C7), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { if(count > 0) vm.playMix() else onAddMusic() }, colors = ButtonDefaults.buttonColors(containerColor = Acid, contentColor = Color(0xFF1A2411)), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)) {
                            Icon(if(count > 0) Icons.Rounded.PlayArrow else Icons.Rounded.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text(if(count > 0) "Play my mix" else "Add music", fontWeight = FontWeight.Bold)
                        }
                    }
                    Box(Modifier.width(94.dp).height(164.dp), contentAlignment = Alignment.Center) {
                        val covers = fresh.take(3)
                        Box(Modifier.size(83.dp).graphicsLayer { rotationZ = 13f; translationY = -30f; translationX = 6f; alpha = .65f }) { Artwork(covers.getOrNull(2)?.song?.artwork, covers.getOrNull(2)?.song?.title ?: "Frequency", Modifier.fillMaxSize()) }
                        Box(Modifier.size(86.dp).graphicsLayer { rotationZ = -10f; translationY = 8f; translationX = -2f }) { Artwork(covers.getOrNull(1)?.song?.artwork, covers.getOrNull(1)?.song?.title ?: "Music", Modifier.fillMaxSize()) }
                        Box(Modifier.size(80.dp).graphicsLayer { rotationZ = 9f; translationY = 46f; translationX = 10f }) { Artwork(covers.firstOrNull()?.song?.artwork, covers.firstOrNull()?.song?.title ?: "Pulsify", Modifier.fillMaxSize()) }
                    }
                }
            }
        } }
        if(scanning) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) }
        if(playback.item != null) item("resume") { Entrance(3) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                SectionTitle("Keep the feeling")
                Surface(onClick = onNow, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(playback.item?.mediaMetadata?.artworkUri?.path, playback.item?.mediaMetadata?.title.toString(), Modifier.size(60.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(playback.item?.mediaMetadata?.title.toString(), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text("${time(playback.position)} · ${playback.item?.mediaMetadata?.artist}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalIconButton(onClick = vm::toggle) { Icon(if(playback.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Resume or pause") }
                    }
                }
            }
        } }
        if(history.isNotEmpty()) item("recent") { MusicShelf("Jump back in", history, { onLibrary("Recently played") }, { track -> vm.play(track.song, history.map { it.song }) }, onMore) }
        item("quick") { Entrance(3) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CollectionShortcut("Liked songs", "${liked.size}${if(liked.size == 12) "+" else ""} favorites", Icons.Rounded.Favorite, Color(0xFF6750A4), Modifier.weight(1f)) { onLibrary("Favorites") }
                CollectionShortcut("Heavy rotation", "Your most played", Icons.Rounded.AllInclusive, Color(0xFF275D52), Modifier.weight(1f)) { onLibrary("Most played") }
            }
        } }
        if(fresh.isNotEmpty()) item("new") { MusicShelf("Fresh finds", fresh, { onLibrary("Recently added") }, { track -> vm.play(track.song, fresh.map { it.song }) }, onMore) }
        if(liked.isNotEmpty()) item("liked") { MusicShelf("Made of favorites", liked, { onLibrary("Favorites") }, { track -> vm.play(track.song, liked.map { it.song }) }, onMore) }
        item("studio") { Entrance(4) {
            Surface(onClick = onEffects, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .6f)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Equalizer, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) { Text("Find your sound", fontWeight = FontWeight.Bold); Text("Equalizer, bass & space. Right here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            }
        } }
        if(count == 0 && !scanning) item { EmptyState("Your collection starts here", "Choose a music folder to fill this space with your songs, albums, and favorite artists.", action = "Choose a folder", onClick = onAddMusic) }
        item { Text("$count songs · No ads. No accounts. Just you.", Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
}
@Composable fun MusicShelf(title: String, tracks: List<Track>, onAll: () -> Unit, onPlay: (Track) -> Unit, onMore: (Track) -> Unit) {
    Column {
        Box(Modifier.padding(horizontal = 20.dp)) { SectionTitle(title, "View all", onAll) }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(tracks, key = { it.song.id }) { track ->
                Column(Modifier.width(142.dp).combinedClickableCompat({ onPlay(track) }, { onMore(track) })) {
                    Artwork(track.song.artwork, track.song.title, Modifier.size(142.dp))
                    Text(track.song.title, Modifier.padding(top = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(track.song.artist, Modifier.padding(top = 3.dp, bottom = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: () -> Unit) = clip(RoundedCornerShape(10.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick)
@Composable private fun CollectionShortcut(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick, modifier, shape = RoundedCornerShape(18.dp), color = color.copy(alpha = .2f)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = color) { Icon(icon, null, Modifier.padding(10.dp).size(22.dp), tint = Color.White) }
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
