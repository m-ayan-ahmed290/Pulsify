package com.pulsify.music.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Your playlists", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("A soundtrack for every mood", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
            FilledIconButton(onClick = onCreate) { Icon(Icons.Rounded.Add, "Create playlist") }
        }
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AssistChip(onClick = { import.launch(arrayOf("application/json", "text/plain")) }, label = { Text("Import playlist") }, leadingIcon = { Icon(Icons.Rounded.FileDownload, null, Modifier.size(18.dp)) })
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if(playlists.isEmpty()) item { EmptyState("Make it a mixtape", "Road trips, slow mornings, late nights.\nGive your favorite songs a place to belong.", Icons.AutoMirrored.Rounded.QueueMusic, "Create a playlist", onCreate) }
            items(playlists, key = { it.playlist.id }) { summary ->
                var menu by remember { mutableStateOf(false) }
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.animateItem()) {
                    Row(Modifier.fillMaxWidth().clickable { onOpen(summary.playlist) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, Modifier.padding(16.dp).size(28.dp), tint = MaterialTheme.colorScheme.primary) }
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(summary.playlist.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            Text("${summary.count} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            ActionIcon(Icons.Rounded.MoreHoriz, "Playlist options") { menu = true }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; rename = summary.playlist })
                                DropdownMenuItem(text = { Text("Export JSON") }, onClick = { menu = false; exporting = summary.playlist; export.launch("${summary.playlist.name.replace(Regex("[^a-zA-Z0-9 _-]"), "_")}.json") })
                                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; delete = summary.playlist })
                            }
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
@Composable fun SettingsScreen(vm: MusicViewModel, onFolder: () -> Unit, onAccess: () -> Unit, onEqualizer: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val count by vm.count.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var theme by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item { Text("Make it yours", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("A few things to fine-tune your listening.", Modifier.padding(top = 8.dp, bottom = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
        item { SettingRow("Equalizer & audio effects", "Open your device’s supported audio controls", Icons.Rounded.Equalizer, onEqualizer) }
        item { Text("Gapless playback is enabled for compatible files. Playback speed and sleep timer are available in Now Playing.", Modifier.padding(horizontal = 8.dp, vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { SectionTitle("App & storage") }
        item { SettingRow("Clear temporary cache", "Remove cached images; your library stays intact", Icons.Rounded.CleaningServices, { vm.clearArtworkCache() }) }
        item { SettingRow("Android permissions", "Manage audio access in system settings", Icons.Rounded.Security, {
            context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }) }
        item { SettingRow("About Pulsify", "Local music. No account. No tracking.", Icons.Rounded.Info, { info = true }) }
        item { Text("PULSIFY", Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
}
@Composable private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(subtitle) }, trailingContent = { Switch(checked, onCheckedChange = onChange) },
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
}
