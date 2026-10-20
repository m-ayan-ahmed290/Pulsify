@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.pulsify.music.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
        val scope = rememberCoroutineScope()
        var selected by remember { mutableStateOf<Track?>(null) }
        var playlistSongs by remember { mutableStateOf<List<String>?>(null) }
        var addSelection by remember { mutableStateOf(false) }
        var create by remember { mutableStateOf(false) }
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
        fun equalizer() {
            vm.audioSession { session ->
                val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).putExtra(AudioEffect.EXTRA_AUDIO_SESSION, session)
                    .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName).putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                try { context.startActivity(intent) } catch(_: android.content.ActivityNotFoundException) { vm.message.value = "This device does not provide an audio effects panel." }
            }
        }
        LaunchedEffect(message) { message?.let { snack.showSnackbar(it); if(vm.message.value == it) vm.message.value = null } }
        LaunchedEffect(settings.folder) {
            if(settings.folder.isNotEmpty() || ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) vm.scan()
        }
        var firstResume by remember { mutableStateOf(true) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            if(firstResume) firstResume = false
            else if(settings.folder.isNotEmpty() || ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) vm.scan()
        }
        val keyboard = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val mainRoute = route in listOf("home", "library", "playlists", "search", "settings")
        Scaffold(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding(), snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                if(mainRoute && !keyboard) Column {
                    AnimatedVisibility(playback.item != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        MiniPlayer(playback, { nav.navigate("now") }, vm::toggle, vm::next)
                    }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, windowInsets = WindowInsets(0,0,0,0)) {
                        val tabs = listOf(Triple("home", "Home", Icons.Rounded.Home), Triple("library", "Library", Icons.Rounded.LibraryMusic),
                            Triple("playlists", "Playlists", Icons.AutoMirrored.Rounded.QueueMusic), Triple("search", "Search", Icons.Rounded.Search), Triple("settings", "Settings", Icons.Rounded.Tune))
                        tabs.forEach { (path, label, icon) -> NavigationBarItem(selected = route == path, onClick = {
                            if(path == "library" && route != path) vm.filter.value = LibraryFilter()
                            if(path == "search") vm.filter.value = LibraryFilter()
                            nav.navigate(path) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true }
                        }, icon = { Icon(icon, label) }, label = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall) }) }
                    }
                }
            }) { padding ->
            NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding),
                enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(240)) { it / 18 } },
                exitTransition = { fadeOut(tween(150)) }, popEnterTransition = { fadeIn(tween(220)) }, popExitTransition = { fadeOut(tween(150)) }) {
                composable("home") { HomeScreen(vm, ::library, { nav.navigate("settings") }, { selected = it }, { nav.navigate("now") }) }
                composable("library") {
                    BackHandler(filter.group != null || filter.playlist != null) { vm.filter.value = filter.copy(group = null, playlist = null) }
                    LibraryScreen(vm, { selected = it }, { folderPicker.launch(null) }, { requestAudio.launch(permission) }, { addSelection = true })
                }
                composable("search") { SearchScreen(vm, { selected = it }, { playlist -> vm.filter.value = LibraryFilter(playlist = playlist.id); nav.navigate("library") }) }
                composable("playlists") { PlaylistsScreen(vm, { create = true }, { playlist -> vm.filter.value = LibraryFilter(playlist = playlist.id); nav.navigate("library") }) }
                composable("settings") { SettingsScreen(vm, { folderPicker.launch(null) }, { requestAudio.launch(permission) }, ::equalizer) }
                composable("now") { NowPlayingScreen(vm, { nav.popBackStack() }, { nav.navigate("queue") }, ::openLyrics, { vm.current.value?.let { selected = it } }) }
                composable("queue") { QueueScreen(vm, { nav.popBackStack() }) }
            }
        }
        if(create) NameDialog("New playlist", onDismiss = { create = false }, onSave = vm::createPlaylist)
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
            } }, confirmButton = { TextButton(onClick = { playlistSongs = null; addSelection = false; create = true }) { Text("New playlist") } },
            dismissButton = { TextButton(onClick = { playlistSongs = null; addSelection = false }) { Text("Cancel") } })
        details?.let { song -> AlertDialog(onDismissRequest = { details = null }, title = { Text("Track details") }, text = {
            Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Title" to song.title, "Artist" to song.artist, "Album" to song.album, "Album artist" to song.albumArtist,
                    "Genre" to song.genre, "Year" to song.year.toString(), "Track" to song.track.toString(), "Duration" to time(song.duration), "Folder" to song.folder,
                    "File" to song.id).forEach { (key,value) -> Text("$key\n${value.ifBlank { "Unknown" }}", style = MaterialTheme.typography.bodyMedium) }
            }
        }, confirmButton = { TextButton(onClick = { details = null }) { Text("Done") } }) }
        if(lyricsOpen) AlertDialog(onDismissRequest = { lyricsOpen = false }, title = { Text("Lyrics") }, text = {
            Text(lyrics ?: "Attach a local .txt or .lrc file for this song. Your lyrics stay on this device.", Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()))
        }, confirmButton = { TextButton(onClick = { lyricsPicker.launch(arrayOf("text/*", "application/octet-stream")) }) { Text("Attach file") } },
            dismissButton = { TextButton(onClick = { lyricsOpen = false }) { Text("Close") } })
    }
}
@Composable fun MenuAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    TextButton(onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp)) {
        Icon(icon, null); Spacer(Modifier.width(16.dp)); Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
    }
}
