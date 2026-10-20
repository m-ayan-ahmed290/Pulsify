package com.pulsify.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.pulsify.music.data.*
import com.pulsify.music.domain.*

@Composable fun LibraryScreen(vm: MusicViewModel, onMore: (Track) -> Unit, onFolder: () -> Unit, onAccess: () -> Unit,
    onAddSelection: () -> Unit, onAddSongs: (List<String>) -> Unit, onSettings: () -> Unit) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val total by vm.count.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val tracks = vm.tracks.collectAsLazyPagingItems()
    var sortMenu by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(filter.section, filter.group, filter.playlist) { selection = emptySet() }
    BackHandler(selection.isNotEmpty()) { selection = emptySet() }
    fun select(id: String) { selection = if(id in selection) selection - id else selection + id }
    val grouped = filter.section in listOf("Albums", "Artists", "Genres", "Folders") && filter.group == null && filter.playlist == null
    val detail = filter.group != null || filter.playlist != null
    val title = filter.group ?: playlists.firstOrNull { it.playlist.id == filter.playlist }?.playlist?.name ?: "Your library"
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = if(detail) 8.dp else 20.dp, end = 12.dp, top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if(detail) ActionIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to library") { vm.filter.value = filter.copy(group = null, playlist = null) }
            Column(Modifier.weight(1f)) {
                Text(if(detail) filter.groupKind.ifBlank { "PLAYLIST" }.uppercase() else "YOUR COLLECTION", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if(!detail) ActionIcon(Icons.Rounded.Tune, "Library settings", onClick = onSettings)
            ActionIcon(Icons.Rounded.Refresh, "Rescan library", onClick = { vm.scan(manual = true) })
        }
        if(!detail) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Songs", "Albums", "Artists", "Genres", "Folders", "Favorites", "Recently added", "Recently played", "Most played").forEach { section ->
                FilterChip(filter.section == section, onClick = { vm.filter.value = filter.copy(section = section, group = null) }, label = { Text(if(section == "Favorites") "Liked" else section) }, shape = CircleShape)
            }
        }
        if(scanning) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp))
        AnimatedContent(targetState = grouped, modifier = Modifier.weight(1f), transitionSpec = {
            (fadeIn(tween(320)) + slideInVertically(tween(360)) { it / 12 }).togetherWith(fadeOut(tween(160)))
        }, label = "Collection view") { showGroups ->
            if(showGroups) {
                if(groups.isEmpty()) EmptyState("Build your collection", "Your albums, artists and folders will live here.", action = "Choose a folder", onClick = onFolder)
                LazyVerticalGrid(columns = GridCells.Adaptive(140.dp), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    items(groups, key = { it.name }) { group -> Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { vm.filter.value = filter.copy(group = group.name, groupKind = filter.section) }.animateItem()) {
                        Box(if(filter.section == "Artists") Modifier.clip(CircleShape) else Modifier) { Artwork(group.artwork, group.name, Modifier.fillMaxWidth().aspectRatio(1f)) }
                        Text(group.name, Modifier.padding(top = 10.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text("${group.count} songs", Modifier.padding(top = 3.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    } }
                }
            } else Column(Modifier.fillMaxSize()) {
                AnimatedVisibility(selection.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Column(Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) { ActionIcon(Icons.Rounded.Close, "Cancel selection") { selection = emptySet() }; Text("${selection.size} selected", fontWeight = FontWeight.Bold) }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                TextButton(onClick = { vm.playSelection(selection.toList()); selection = emptySet() }) { Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp)); Text("Play") }
                                TextButton(onClick = { vm.enqueueSelection(selection.toList()); selection = emptySet() }) { Icon(Icons.Rounded.AddToQueue, null, Modifier.size(18.dp)); Text("Queue") }
                                TextButton(onClick = { onAddSongs(selection.toList()); selection = emptySet() }) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, Modifier.size(18.dp)); Text("Playlist") }
                            }
                        }
                    }
                }
                if(selection.isEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("${tracks.itemCount} songs", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if(!detail && tracks.itemCount > 0) Text("Hold a song to select", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f))
                    }
                    if(filter.playlist == null && filter.section !in listOf("Recently added", "Recently played", "Most played")) Box {
                        ActionIcon(Icons.AutoMirrored.Rounded.Sort, "Sort songs") { sortMenu = true }
                        DropdownMenu(sortMenu, { sortMenu = false }) {
                            Sort.entries.forEach { sort -> DropdownMenuItem(text = { Text((if(filter.sort == sort) "✓ " else "") + sort.label) }, onClick = { vm.filter.value = filter.copy(sort = sort); sortMenu = false }) }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text((if(filter.descending) "✓ " else "") + "Descending") }, onClick = { vm.filter.value = filter.copy(descending = !filter.descending); sortMenu = false })
                        }
                    }
                    ActionIcon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add collection to playlist", onClick = onAddSelection)
                    FilledTonalIconButton(onClick = { vm.playAll(true) }, enabled = tracks.itemCount > 0) { Icon(Icons.Rounded.Shuffle, "Shuffle songs") }
                    FilledIconButton(onClick = { vm.playAll() }, enabled = tracks.itemCount > 0) { Icon(Icons.Rounded.PlayArrow, "Play songs") }
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                    if(detail && tracks.itemCount > 0) item("cover") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            val song = tracks.peek(0)?.song
                            Artwork(song?.artwork, title, Modifier.size(94.dp))
                            Column(Modifier.padding(start = 18.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis); Text("${tracks.itemCount} songs · On this device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    if(tracks.loadState.refresh is LoadState.Loading && tracks.itemCount == 0) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(20.dp)) }
                    if(tracks.itemCount == 0 && tracks.loadState.refresh !is LoadState.Loading) item {
                        if(total == 0) {
                            EmptyState("Your music, all together", "Choose a folder or connect your device audio library to start listening.", action = "Choose music folder", onClick = onFolder)
                            TextButton(onClick = onAccess, modifier = Modifier.fillMaxWidth()) { Text("Scan device audio instead") }
                        } else when {
                            filter.playlist != null -> EmptyState("Give this mix a soundtrack", "Hold songs in your library to select and add them here.", Icons.AutoMirrored.Rounded.QueueMusic)
                            filter.section == "Favorites" -> EmptyState("Start with a little love", "Tap the heart on a song to keep it here.", Icons.Rounded.FavoriteBorder)
                            filter.section in listOf("Recently played", "Most played") -> EmptyState("Your next obsession awaits", "Listen for 30 seconds or half a track to build your history.", Icons.Rounded.History)
                            else -> EmptyState("No songs here yet", "Try another collection or rescan for new music.")
                        }
                    }
                    if(tracks.loadState.refresh is LoadState.Error) item { TextButton(onClick = { tracks.retry() }) { Text("Library could not load. Tap to retry.") } }
                    items(tracks.itemCount, key = tracks.itemKey { it.song.id }) { index ->
                        val track = tracks[index]
                        if(track == null) Spacer(Modifier.fillMaxWidth().height(76.dp))
                        else Box(Modifier.animateItem()) { SongRow(track.song, track.favorite, playback.item?.mediaId == track.song.id,
                            { if(selection.isNotEmpty()) select(track.song.id) else vm.play(track.song) }, { onMore(track) },
                            onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); select(track.song.id) }, selected = if(selection.isNotEmpty()) track.song.id in selection else null) }
                    }
                }
            }
        }
    }
}
@Composable fun SearchScreen(vm: MusicViewModel, onMore: (Track) -> Unit, onPlaylist: (Playlist) -> Unit, onBrowse: (String) -> Unit) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val searches by vm.searchHistory.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val tracks = vm.tracks.collectAsLazyPagingItems()
    val keyboard = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        if(!keyboard) Entrance { Column(Modifier.padding(top = 22.dp, bottom = 18.dp)) { Text("A SOUND FOR EVERY MOMENT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary); Text("Find your next favorite.", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 6.dp)) } }
        if(keyboard) Spacer(Modifier.height(8.dp))
        KeyboardSafeField(filter.query, { vm.filter.value = LibraryFilter(query = it.take(200)) }, "Search your music", onDone = { vm.rememberSearch(filter.query) },
            leading = { Icon(Icons.Rounded.Search, null) }, trailing = if(filter.query.isNotEmpty()) { { ActionIcon(Icons.Rounded.Close, "Clear search") { vm.filter.value = LibraryFilter() } } } else null)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp)) {
            if(filter.query.isBlank()) {
                if(searches.isNotEmpty()) {
                    item { SectionTitle("Recent searches", "Clear", { vm.clearSearchHistory() }) }
                    items(searches, key = { "search-$it" }) { term -> ListItem(headlineContent = { Text(term, maxLines = 1, overflow = TextOverflow.Ellipsis) }, leadingContent = { Icon(Icons.Rounded.History, null) }, trailingContent = { Icon(Icons.Rounded.NorthWest, null) }, modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable { vm.filter.value = LibraryFilter(query = term); vm.rememberSearch(term) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)) }
                }
                item { SectionTitle("Browse your world") }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(listOf("Albums" to Color(0xFF6651A5), "Artists" to Color(0xFF316A68)), listOf("Genres" to Color(0xFFAB6352), "Folders" to Color(0xFF35608C))).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { pair.forEach { (name,color) -> Surface(onClick = { onBrowse(name) }, modifier = Modifier.weight(1f), color = color, shape = RoundedCornerShape(18.dp)) {
                                Box(Modifier.heightIn(min = 122.dp).background(Brush.linearGradient(listOf(color, color.copy(alpha = .7f)))).padding(16.dp)) { Text(name, color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.TopStart)); Icon(when(name) { "Albums" -> Icons.Rounded.Album; "Artists" -> Icons.Rounded.Person; "Genres" -> Icons.Rounded.GraphicEq; else -> Icons.Rounded.Folder }, null, Modifier.align(Alignment.BottomEnd).size(42.dp), tint = Color.White.copy(alpha = .55f)) }
                            } } }
                        }
                    }
                }
                item { Text("Search songs, artists, albums, playlists, genres, and folders. Everything stays on your device.", Modifier.padding(vertical = 22.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                val matching = playlists.filter { it.playlist.name.contains(filter.query, true) }
                if(matching.isNotEmpty()) {
                    item { SectionTitle("Playlists") }
                    items(matching, key = { "playlist-${it.playlist.id}" }) { summary -> ListItem(headlineContent = { Text(summary.playlist.name) }, supportingContent = { Text("${summary.count} songs") }, leadingContent = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) }, modifier = Modifier.clickable { vm.rememberSearch(filter.query); onPlaylist(summary.playlist) }) }
                }
                if(tracks.loadState.refresh is LoadState.Loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(12.dp)) }
                if(tracks.itemCount > 0) item { SectionTitle("Songs · ${tracks.itemCount}") }
                items(tracks.itemCount, key = tracks.itemKey { it.song.id }) { i -> tracks[i]?.let { track -> Box(Modifier.animateItem()) { SongRow(track.song, track.favorite, playback.item?.mediaId == track.song.id, { vm.rememberSearch(filter.query); vm.play(track.song) }, { onMore(track) }) } } ?: Spacer(Modifier.height(76.dp)) }
                if(tracks.itemCount == 0 && matching.isEmpty() && tracks.loadState.refresh !is LoadState.Loading) item { EmptyState("No matches this time", "Try another name, artist, or album. A library rescan can pick up newly added music.", Icons.Rounded.SearchOff) }
            }
        }
    }
}
