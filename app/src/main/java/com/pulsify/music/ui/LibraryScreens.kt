package com.pulsify.music.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.pulsify.music.R
import com.pulsify.music.data.*
import com.pulsify.music.domain.*
import java.time.LocalTime

@Composable fun HomeScreen(vm: MusicViewModel, onLibrary: (String) -> Unit, onSettings: () -> Unit, onMore: (Track) -> Unit, onNow: () -> Unit) {
    val count by vm.count.collectAsStateWithLifecycle()
    val tracks by vm.home.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val greeting = when(LocalTime.now().hour) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("YOUR MUSIC, YOUR MOMENT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp))
                    Text(greeting, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                Surface(shape = RoundedCornerShape(16.dp), color = Violet) { Image(painterResource(R.mipmap.pulsify_mark), "Pulsify", Modifier.size(48.dp)) }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(28.dp), color = Color(0xFF31224C), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFF443064), Color(0xFF201D31)))).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.GraphicEq, null, tint = Violet, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp)); Text("MADE FOR YOUR COLLECTION", color = Violet, style = MaterialTheme.typography.labelSmall)
                    }
                    Text("A little escape.\nA lot of good music.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(if(count == 0) "Your favorite sounds, all in one place.\nAdd your music and make yourself at home." else "$count songs. Endless possibilities.\nRediscover something you love.", color = Color(0xFFD1C7DF), style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(onClick = { if(count == 0) onSettings() else { vm.filter.value = LibraryFilter(); vm.playAll(true) } }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Violet, contentColor = Color(0xFF26133F))) {
                        Icon(if(count == 0) Icons.Rounded.FolderOpen else Icons.Rounded.Shuffle, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text(if(count == 0) "Add your music" else "Shuffle my music")
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickCard("Favorites", "Made of your favorites", Icons.Rounded.FavoriteBorder, Modifier.weight(1f)) { onLibrary("Favorites") }
                QuickCard("Recently played", "Back to the good stuff", Icons.Rounded.History, Modifier.weight(1f)) { onLibrary("Recently played") }
            }
        }
        if(playback.item != null) item {
            SectionTitle("Pick up where you left off")
            Surface(onClick = onNow, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(playback.item?.mediaMetadata?.artworkUri?.path, playback.item?.mediaMetadata?.title.toString(), Modifier.size(64.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(playback.item?.mediaMetadata?.title.toString(), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text("${time(playback.position)} · ${playback.item?.mediaMetadata?.artist}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ActionIcon(if(playback.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Resume or pause", onClick = vm::toggle)
                }
            }
        }
        item { SectionTitle("Fresh in your library", if(tracks.isNotEmpty()) "See all" else null) { onLibrary("Recently added") } }
        if(scanning && tracks.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if(tracks.isEmpty()) item { EmptyState("Your next favorite is waiting", "Choose a music folder or connect your device library to get started.", action = "Set up library", onClick = onSettings) }
        else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                    items(tracks.take(8), key = { it.song.id }) { track ->
                        Column(Modifier.width(148.dp).clip(RoundedCornerShape(20.dp)).clickable { vm.play(track.song, tracks.map { it.song }) }) {
                            Artwork(track.song.artwork, track.song.title, Modifier.size(148.dp))
                            Text(track.song.title, Modifier.padding(top = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text(track.song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item { SectionTitle("On your radar") }
            items(tracks.take(5), key = { "list-${it.song.id}" }) { track -> SongRow(track.song, track.favorite, playback.item?.mediaId == track.song.id, { vm.play(track.song, tracks.map { it.song }) }, { onMore(track) }) }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.OfflineBolt, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("  All yours. Always offline.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
@Composable private fun QuickCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick, modifier, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable fun LibraryScreen(vm: MusicViewModel, onMore: (Track) -> Unit, onFolder: () -> Unit, onAccess: () -> Unit, onAddSelection: () -> Unit) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val total by vm.count.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val tracks = vm.tracks.collectAsLazyPagingItems()
    var sortMenu by remember { mutableStateOf(false) }
    val grouped = filter.section in listOf("Albums", "Artists", "Genres", "Folders") && filter.group == null && filter.playlist == null
    val title = filter.group ?: playlists.firstOrNull { it.playlist.id == filter.playlist }?.playlist?.name ?: "Your library"
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if(filter.group != null || filter.playlist != null) ActionIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to library") { vm.filter.value = filter.copy(group = null, playlist = null) }
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            ActionIcon(Icons.Rounded.Refresh, "Rescan library", onClick = { vm.scan() })
            if(!grouped && filter.playlist == null && filter.section !in listOf("Recently added", "Recently played", "Most played")) Box {
                ActionIcon(Icons.AutoMirrored.Rounded.Sort, "Sort songs") { sortMenu = true }
                DropdownMenu(sortMenu, { sortMenu = false }) {
                    Sort.entries.forEach { sort -> DropdownMenuItem(text = { Text((if(filter.sort == sort) "✓ " else "") + sort.label) }, onClick = { vm.filter.value = filter.copy(sort = sort); sortMenu = false }) }
                    DropdownMenuItem(text = { Text(if(filter.descending) "✓ Descending" else "Ascending → descending") }, onClick = { vm.filter.value = filter.copy(descending = !filter.descending); sortMenu = false })
                }
            }
        }
        if(filter.group == null && filter.playlist == null) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Songs", "Albums", "Artists", "Genres", "Folders", "Favorites", "Recently added", "Recently played", "Most played").forEach { section ->
                FilterChip(selected = filter.section == section, onClick = { vm.filter.value = filter.copy(section = section, group = null) }, label = { Text(section) })
            }
        }
        if(scanning) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp))
        if(grouped) {
            if(groups.isEmpty()) EmptyState("Nothing here yet", "Scan your device or choose a folder in Settings.", action = "Choose folder", onClick = onFolder)
            LazyVerticalGrid(columns = GridCells.Adaptive(140.dp), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                items(groups, key = { it.name }) { group -> Column(Modifier.clip(RoundedCornerShape(18.dp)).clickable { vm.filter.value = filter.copy(group = group.name, groupKind = filter.section) }.animateItem()) {
                    Artwork(group.artwork, group.name, Modifier.fillMaxWidth().aspectRatio(1f))
                    Text(group.name, Modifier.padding(top = 10.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text("${group.count} songs", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                } }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${tracks.itemCount} songs", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ActionIcon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add selection to playlist", onClick = onAddSelection)
                FilledTonalIconButton(onClick = { vm.playAll(true) }, enabled = tracks.itemCount > 0) { Icon(Icons.Rounded.Shuffle, "Shuffle songs") }
                FilledIconButton(onClick = { vm.playAll() }, enabled = tracks.itemCount > 0) { Icon(Icons.Rounded.PlayArrow, "Play songs") }
            }
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                if(tracks.itemCount == 0 && tracks.loadState.refresh !is LoadState.Loading) item {
                    if(total == 0) {
                        EmptyState("Make room for good music", "Your songs will appear here. Allow device audio access or select a specific music folder.", action = "Choose music folder", onClick = onFolder)
                        TextButton(onClick = onAccess, modifier = Modifier.fillMaxWidth()) { Text("Scan device audio instead") }
                    } else when {
                        filter.playlist != null -> EmptyState("Give this mix a soundtrack", "Open a song’s options in your library to add it to this playlist.", Icons.AutoMirrored.Rounded.QueueMusic)
                        filter.section == "Favorites" -> EmptyState("Keep your favorites close", "Tap the heart on a song to find it here.", Icons.Rounded.FavoriteBorder)
                        filter.section in listOf("Recently played", "Most played") -> EmptyState("Your listening starts here", "Listen to a song for 30 seconds or half its length to start your history.", Icons.Rounded.History)
                        else -> EmptyState("No songs here yet", "Try another collection or rescan for new music.")
                    }
                }
                if(tracks.loadState.refresh is LoadState.Error) item { Text("Could not load this library."); TextButton(onClick = { tracks.retry() }) { Text("Retry") } }
                items(tracks.itemCount, key = tracks.itemKey { it.song.id }) { index -> tracks[index]?.let { track ->
                    Box(Modifier.animateItem()) { SongRow(track.song, track.favorite, playback.item?.mediaId == track.song.id, { vm.play(track.song) }, { onMore(track) }) }
                } }
                if(tracks.loadState.append is LoadState.Loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
        }
    }
}
@Composable fun SearchScreen(vm: MusicViewModel, onMore: (Track) -> Unit, onPlaylist: (Playlist) -> Unit) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val tracks = vm.tracks.collectAsLazyPagingItems()
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Find your sound", Modifier.padding(top = 20.dp, bottom = 16.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        KeyboardSafeField(filter.query, { vm.filter.value = LibraryFilter(query = it) }, "Songs, artists, albums, genres, folders")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 16.dp)) {
            if(filter.query.isBlank()) item { EmptyState("Something on your mind?", "Search your collection for a song, an artist, an album, or a playlist.", Icons.Rounded.Search) }
            else {
                val matching = playlists.filter { it.playlist.name.contains(filter.query, true) }
                if(matching.isNotEmpty()) {
                    item { SectionTitle("Playlists") }
                    items(matching, key = { "playlist-${it.playlist.id}" }) { summary -> ListItem(headlineContent = { Text(summary.playlist.name) }, supportingContent = { Text("${summary.count} songs") }, leadingContent = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) }, modifier = Modifier.clickable { onPlaylist(summary.playlist) }) }
                }
                if(tracks.itemCount > 0) item { SectionTitle("Music · ${tracks.itemCount}") }
                items(tracks.itemCount, key = tracks.itemKey { it.song.id }) { i -> tracks[i]?.let { track -> SongRow(track.song, track.favorite, playback.item?.mediaId == track.song.id, { vm.play(track.song) }, { onMore(track) }) } }
                if(tracks.itemCount == 0 && matching.isEmpty() && tracks.loadState.refresh !is LoadState.Loading) item { EmptyState("No matches yet", "Try a different spelling or scan for newly added music.", Icons.Rounded.SearchOff) }
            }
        }
    }
}
