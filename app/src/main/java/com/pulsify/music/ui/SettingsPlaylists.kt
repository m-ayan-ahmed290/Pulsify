package com.pulsify.music.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulsify.music.data.Playlist

@Composable fun PlaylistsScreen(vm: MusicViewModel, onCreate: () -> Unit, onOpen: (Playlist) -> Unit) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    var rename by remember { mutableStateOf<Playlist?>(null) }
    var delete by remember { mutableStateOf<Playlist?>(null) }
    var exporting by remember { mutableStateOf<Playlist?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri != null) exporting?.let { vm.exportPlaylist(it.id, it.name, uri) }; exporting = null
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri != null) vm.importPlaylist(uri) }
    LazyVerticalGrid(columns = GridCells.Adaptive(148.dp), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) { Entrance {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("YOUR MIXTAPES", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall); Text("Made by you.", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 5.dp)) }
                FilledTonalIconButton(onClick = onCreate) { Icon(Icons.Rounded.Add, "Create playlist") }
            }
        } }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AssistChip(onClick = onCreate, label = { Text("New playlist") }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(17.dp)) })
                AssistChip(onClick = { import.launch(arrayOf("application/json", "text/plain")) }, label = { Text("Import JSON") }, leadingIcon = { Icon(Icons.Rounded.FileDownload, null, Modifier.size(17.dp)) })
            }
        }
        if(playlists.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("Every mood needs a mixtape", "Road trips. Slow mornings. Late nights.\nPut your favorite songs together.", Icons.AutoMirrored.Rounded.QueueMusic, "Create a playlist", onCreate) }
        items(playlists, key = { it.playlist.id }) { summary ->
            var menu by remember { mutableStateOf(false) }
            Column(Modifier.animateItem()) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).clickable { onOpen(summary.playlist) }) {
                    Artwork(summary.artwork, summary.playlist.name, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .45f)))))
                    Text("MIXTAPE", Modifier.align(Alignment.BottomStart).padding(14.dp), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .8f))
                    FilledIconButton(onClick = { vm.playPlaylist(summary.playlist.id) }, enabled = summary.count > 0, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Acid, contentColor = Color(0xFF19210F))) { Icon(Icons.Rounded.PlayArrow, "Play ${summary.playlist.name}") }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { onOpen(summary.playlist) }.padding(top = 10.dp)) {
                        Text(summary.playlist.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("${summary.count} songs", Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        ActionIcon(Icons.Rounded.MoreHoriz, "Options for ${summary.playlist.name}") { menu = true }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Shuffle play") }, enabled = summary.count > 0, onClick = { menu = false; vm.playPlaylist(summary.playlist.id, true) })
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; rename = summary.playlist })
                            DropdownMenuItem(text = { Text("Export JSON") }, onClick = { menu = false; exporting = summary.playlist; export.launch("${summary.playlist.name.replace(Regex("[^a-zA-Z0-9 _-]"), "_")}.json") })
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; delete = summary.playlist })
                        }
                    }
                }
            }
        }
    }
    rename?.let { playlist -> NameDialog("Rename playlist", playlist.name, { rename = null }) { vm.renamePlaylist(playlist.id, it) } }
    delete?.let { playlist -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("Delete ${playlist.name}?") }, text = { Text("Your music files will be kept.") },
        confirmButton = { TextButton(onClick = { vm.deletePlaylist(playlist.id); delete = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { delete = null }) { Text("Cancel") } }) }
}
@Composable fun SettingsScreen(vm: MusicViewModel, onFolder: () -> Unit, onAccess: () -> Unit, onEqualizer: () -> Unit, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val count by vm.count.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var theme by remember { mutableStateOf(false) }
    var skipPicker by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item { Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) { ActionIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack); Column(Modifier.padding(start = 6.dp)) { Text("THE WAY YOU LISTEN", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall); Text("Your space", style = MaterialTheme.typography.headlineLarge) } } }
        item { SettingRow("Sound Studio", "Your equalizer, presets, bass & headphone space", Icons.Rounded.Equalizer, onEqualizer) }
        item { SectionTitle("Music library") }
        item { SettingRow("Music folder", if(settings.folder.isEmpty()) "Device audio library" else Uri.decode(settings.folder).substringAfterLast("tree/"), Icons.Rounded.FolderOpen, onFolder) }
        item { SettingRow("Use device audio library", "Request audio access to scan shared music", Icons.Rounded.LibraryMusic, onAccess) }
        item { SettingRow(if(scanning) "Scanning your music…" else "Rescan library", "$count songs · New and deleted files update automatically", Icons.Rounded.Refresh, { vm.scan() }) }
        item { SettingSwitch("Hide WhatsApp audio", "Keep voice notes out of your library", settings.hideWhatsApp) { vm.flagSetting("hideWhatsApp", it) } }
        item { SettingSwitch("Show hidden folders", "Include folders whose names start with a dot", settings.showHidden) { vm.flagSetting("showHidden", it) } }
        item { SectionTitle("Appearance") }
        item { SettingRow("Theme", settings.theme, Icons.Rounded.Palette, { theme = true }) }
        if(Build.VERSION.SDK_INT >= 31) item { SettingSwitch("Dynamic colors", "Use colors from your Android wallpaper", settings.dynamic) { vm.flagSetting("dynamic", it) } }
        item { SectionTitle("Playback") }
        item { SettingSwitch("Restore listening session", "Reopen your queue and position, ready to resume", settings.resume) { vm.flagSetting("resume", it) } }
        item { SettingRow("Seek distance", "${settings.skip} seconds forward or backward", Icons.Rounded.FastForward, { skipPicker = true }) }
        item { Text("Gapless playback is enabled for compatible files. Speed, A–B repeat, player volume, and sleep timer are in Now Playing.", Modifier.padding(horizontal = 8.dp, vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { SectionTitle("App & storage") }
        item { SettingRow("Clear temporary cache", "Remove cached images; your library stays intact", Icons.Rounded.CleaningServices, { vm.clearArtworkCache() }) }
        item { SettingRow("Android permissions", "Manage audio access in system settings", Icons.Rounded.Security, {
            context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }) }
        item { SettingRow("About Pulsify", "Local music. No account. No tracking.", Icons.Rounded.Info, { info = true }) }
        item { Text("PULSIFY", Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
    if(skipPicker) AlertDialog(onDismissRequest = { skipPicker = false }, title = { Text("Seek distance") }, text = { Column { listOf(5,10,15,30,60).forEach { seconds -> TextButton(onClick = { vm.skipSetting(seconds); skipPicker = false }, modifier = Modifier.fillMaxWidth()) { Text("$seconds seconds" + if(settings.skip == seconds) " ✓" else "") } } } }, confirmButton = { TextButton(onClick = { skipPicker = false }) { Text("Close") } })
    if(theme) AlertDialog(onDismissRequest = { theme = false }, title = { Text("Choose your theme") }, text = {
        Column { listOf("Light", "Dark", "System").forEach { value ->
            Row(Modifier.fillMaxWidth().clickable { vm.textSetting("theme", value); theme = false }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(settings.theme == value, onClick = { vm.textSetting("theme", value); theme = false }); Text(value)
            }
        } }
    }, confirmButton = { TextButton(onClick = { theme = false }) { Text("Close") } })
    if(info) AlertDialog(onDismissRequest = { info = false }, title = { Text("Pulsify") }, text = {
        Text("An independent music player for your collection.\n\nAndroid ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}\nLibrary: $count songs\n\nMusic stays on your device. Folder access uses Android’s secure picker. Background playback and media notifications are managed by Media3.\n\nOnline downloading, crossfade, and ReplayGain are not included in this version.")
    }, confirmButton = { TextButton(onClick = { info = false }) { Text("Done") } })
}
@Composable private fun SettingRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title, fontWeight = FontWeight.Medium) }, supportingContent = { Text(subtitle, maxLines = 3, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick), colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer))
}
@Composable private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(subtitle) }, trailingContent = { Switch(checked, onCheckedChange = onChange) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).clickable { onChange(!checked) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer))
}
