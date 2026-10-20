@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.pulsify.music.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.pulsify.music.data.*
import com.pulsify.music.domain.LibraryFilter
import kotlinx.coroutines.launch

@Composable fun PulsifyApp(vm: MusicViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    PulsifyTheme(settings) {
        val context = LocalContext.current
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route ?: "home"
        val playback by vm.playback.collectAsStateWithLifecycle()
        val message by vm.message.collectAsStateWithLifecycle()
        val playlists by vm.playlists.collectAsStateWithLifecycle()
        val filter by vm.filter.collectAsStateWithLifecycle()
        val snack = remember { SnackbarHostState() }
        var lastTab by rememberSaveable { mutableStateOf("home") }
        var selected by remember { mutableStateOf<Track?>(null) }
        var playlistSongs by remember { mutableStateOf<List<String>?>(null) }
        var addSelection by remember { mutableStateOf(false) }
        var create by rememberSaveable { mutableStateOf(false) }
        var pendingSongs by remember { mutableStateOf<List<String>>(emptyList()) }
        var pendingSelection by remember { mutableStateOf(false) }
        var details by remember { mutableStateOf<Song?>(null) }
        var lyricsOpen by remember { mutableStateOf(false) }
        var lyricSong by remember { mutableStateOf("") }
        val lyrics by vm.lyrics.collectAsStateWithLifecycle()
        val permission = if(Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        val requestAudio = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if(granted) vm.useDevice() else vm.message.value = "Audio access was denied. You can choose a specific music folder instead, or allow access in Android app settings."
        }
        val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if(uri != null) try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                vm.folder(uri)
            } catch(_: SecurityException) { vm.message.value = "This provider did not grant lasting folder access. Choose another folder." }
        }
        val lyricsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri != null) vm.attachLyrics(lyricSong, uri) }
        fun library(section: String) { vm.filter.value = LibraryFilter(section = section); nav.navigate("library") { launchSingleTop = true } }
        fun openLyrics() { playback.item?.let { lyricSong = it.mediaId; vm.loadLyrics(it.mediaId); lyricsOpen = true } }
        fun equalizer() { nav.navigate("effects") { launchSingleTop = true } }
        LaunchedEffect(message) { message?.let {
            val result = snack.showSnackbar(it, actionLabel = if(it == "Removed from queue") "Undo" else null)
            if(result == SnackbarResult.ActionPerformed && it == "Removed from queue") vm.undoRemove()
            if(vm.message.value == it) vm.message.value = null
        } }
        LaunchedEffect(settings.folder) {
            if(settings.folder.isNotEmpty() || ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) vm.scan()
        }
        var firstResume by remember { mutableStateOf(true) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            if(firstResume) firstResume = false
            else if(settings.folder.isNotEmpty() || ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) vm.scan()
        }
        val keyboard = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val mainRoute = route in primaryRoutes || route == "settings"
        LaunchedEffect(route) { if(route in primaryRoutes) lastTab = route }
        Scaffold(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding(), snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                if(!keyboard) AnimatedVisibility(mainRoute, enter = expandVertically(tween(420)) + slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(320)),
                    exit = shrinkVertically(tween(360)) + fadeOut(tween(240))) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        NavigationDock(if(route in primaryRoutes) route else lastTab, playback, { path ->
                            if(path == "library" && route != path) vm.filter.value = LibraryFilter()
                            if(path == "search" && route != path) vm.filter.value = LibraryFilter()
                            nav.navigate(path) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true }
                        }, { nav.navigate("now") { launchSingleTop = true } }, vm::toggle, vm::next, vm::previous)
                    }
                }
            }) { padding ->
            NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding),
                enterTransition = {
                    if(targetState.destination.route == "now") slideInVertically(tween(520, easing = CubicBezierEasing(.16f,1f,.3f,1f))) { it } + fadeIn(tween(300))
                    else slideInHorizontally(tween(420, easing = FastOutSlowInEasing)) { width ->
                        if(primaryRoutes.indexOf(targetState.destination.route) in 0 until primaryRoutes.indexOf(initialState.destination.route)) -width / 3 else width / 3
                    } + fadeIn(tween(300))
                },
                exitTransition = { if(targetState.destination.route == "now") scaleOut(tween(400), targetScale = .94f) + fadeOut(tween(300)) else slideOutHorizontally(tween(360)) { -it / 7 } + fadeOut(tween(220)) },
                popEnterTransition = { if(initialState.destination.route == "now") scaleIn(tween(450), initialScale = .94f) + fadeIn(tween(300)) else slideInHorizontally(tween(420)) { -it / 3 } + fadeIn(tween(280)) },
                popExitTransition = { if(initialState.destination.route == "now") slideOutVertically(tween(450, easing = FastOutSlowInEasing)) { it } else slideOutHorizontally(tween(360)) { it / 3 } + fadeOut(tween(220)) }) {
                composable("home") { HomeScreen(vm, ::library, { nav.navigate("settings") }, { selected = it }, { nav.navigate("now") }, ::equalizer, { folderPicker.launch(null) }) }
                composable("library") {
                    BackHandler(filter.group != null || filter.playlist != null) { vm.filter.value = filter.copy(group = null, playlist = null) }
                    LibraryScreen(vm, { selected = it }, { folderPicker.launch(null) }, { requestAudio.launch(permission) }, { addSelection = true }, { playlistSongs = it }, { nav.navigate("settings") })
                }
                composable("search") { SearchScreen(vm, { selected = it }, { playlist -> vm.filter.value = LibraryFilter(playlist = playlist.id); nav.navigate("library") }, ::library) }
                composable("playlists") { PlaylistsScreen(vm, { pendingSongs = emptyList(); pendingSelection = false; create = true }, { playlist -> vm.filter.value = LibraryFilter(playlist = playlist.id); nav.navigate("library") }) }
                composable("settings") { SettingsScreen(vm, { folderPicker.launch(null) }, { requestAudio.launch(permission) }, ::equalizer, { nav.popBackStack() }) }
                composable("now") { NowPlayingScreen(vm, { nav.popBackStack() }, { nav.navigate("queue") }, ::openLyrics, { vm.current.value?.let { selected = it } }, ::equalizer) }
                composable("effects") { EffectsScreen(vm, { nav.popBackStack() }) }
                composable("queue") { QueueScreen(vm, { nav.popBackStack() }) }
            }
        }
        if(create) NameDialog("New playlist", onDismiss = { create = false }, onSave = { vm.createPlaylist(it, pendingSongs, pendingSelection); pendingSongs = emptyList(); pendingSelection = false })
        selected?.let { track ->
            ModalBottomSheet(onDismissRequest = { selected = null }) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                    Text(track.song.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(track.song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    MenuAction(if(track.favorite) "Remove from favorites" else "Add to favorites", Icons.Rounded.FavoriteBorder) { vm.favorite(track.song); selected = null }
                    MenuAction("Play next", Icons.AutoMirrored.Rounded.PlaylistPlay) { vm.addQueue(track.song, true); selected = null }
                    MenuAction("Add to queue", Icons.Rounded.AddToQueue) { vm.addQueue(track.song, false); selected = null }
                    MenuAction("Add to playlist", Icons.AutoMirrored.Rounded.PlaylistAdd) { playlistSongs = listOf(track.song.id); selected = null }
                    if(filter.playlist != null) {
                        MenuAction("Move up in playlist", Icons.Rounded.ArrowUpward) { vm.movePlaylistSong(filter.playlist!!, track.song.id, -1); selected = null }
                        MenuAction("Move down in playlist", Icons.Rounded.ArrowDownward) { vm.movePlaylistSong(filter.playlist!!, track.song.id, 1); selected = null }
                        MenuAction("Remove from playlist", Icons.Rounded.RemoveCircleOutline) { vm.removePlaylistSong(filter.playlist!!, track.song.id); selected = null }
                    }
                    MenuAction("Track details", Icons.Rounded.Info) { details = track.song; selected = null }
                }
            }
        }
        if(playlistSongs != null || addSelection) AlertDialog(onDismissRequest = { playlistSongs = null; addSelection = false }, title = { Text("Add to playlist") },
            text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                if(playlists.isEmpty()) Text("Create a playlist to keep your favorite music together.")
                playlists.forEach { summary -> TextButton(onClick = {
                    if(addSelection) vm.addSelection(summary.playlist.id) else vm.addPlaylist(summary.playlist.id, playlistSongs.orEmpty())
                    playlistSongs = null; addSelection = false
                }) { Text(summary.playlist.name) } }
            } }, confirmButton = { TextButton(onClick = { pendingSongs = playlistSongs.orEmpty(); pendingSelection = addSelection; playlistSongs = null; addSelection = false; create = true }) { Text("New playlist") } },
            dismissButton = { TextButton(onClick = { playlistSongs = null; addSelection = false }) { Text("Cancel") } })
        details?.let { song -> AlertDialog(onDismissRequest = { details = null }, title = { Text("Track details") }, text = {
            Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Title" to song.title, "Artist" to song.artist, "Album" to song.album, "Album artist" to song.albumArtist,
                    "Genre" to song.genre, "Year" to song.year.toString(), "Track" to song.track.toString(), "Duration" to time(song.duration), "Folder" to song.folder,
                    "File" to song.id).forEach { (key,value) -> Text("$key\n${value.ifBlank { "Unknown" }}", style = MaterialTheme.typography.bodyMedium) }
            }
        }, confirmButton = { TextButton(onClick = { details = null }) { Text("Done") } }) }
        if(lyricsOpen) ModalBottomSheet(onDismissRequest = { lyricsOpen = false }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("EVERY WORD, EVERY FEELING", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall); Text("Lyrics", style = MaterialTheme.typography.headlineMedium) }
                    TextButton(onClick = { lyricsPicker.launch(arrayOf("text/*", "application/octet-stream")) }) { Text("Attach file") }
                }
                Text(lyrics ?: "Your words belong here.\n\nAttach a local .txt or .lrc file to read along. Lyrics stay on this device.", Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 24.dp), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .8f))
            }
        }
    }
}
@Composable fun MenuAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    TextButton(onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp)) {
        Icon(icon, null); Spacer(Modifier.width(16.dp)); Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
    }
}
