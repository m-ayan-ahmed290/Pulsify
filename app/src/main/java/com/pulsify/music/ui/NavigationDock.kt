package com.pulsify.music.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val primaryRoutes = listOf("home", "search", "library", "playlists")
@Composable fun NavigationDock(route: String, playback: Playback, onNavigate: (String) -> Unit, onPlayer: () -> Unit,
    onToggle: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit) {
    Surface(Modifier.padding(horizontal = 14.dp).padding(top = 4.dp, bottom = 10.dp).widthIn(max = 680.dp).fillMaxWidth(),
        shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .28f)), shadowElevation = 14.dp) {
        Column {
            AnimatedVisibility(playback.item != null, enter = expandVertically(tween(420)) + fadeIn(tween(360)), exit = shrinkVertically(tween(300)) + fadeOut()) {
                MiniPlayer(playback, onPlayer, onToggle, onNext, onPrevious)
            }
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 6.dp).padding(top = 6.dp, bottom = 7.dp)) {
                val width = maxWidth / 4
                val index = primaryRoutes.indexOf(route).coerceAtLeast(0)
                val offset by animateDpAsState(width * index, spring(dampingRatio = .82f, stiffness = 380f), label = "Navigation capsule")
                Surface(Modifier.offset(x = offset).width(width).height(60.dp).padding(horizontal = 3.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {}
                Row(Modifier.fillMaxWidth()) {
                    listOf(Triple("home","Home",Icons.Rounded.Home), Triple("search","Search",Icons.Rounded.Search),
                        Triple("library","Library",Icons.Rounded.LibraryMusic), Triple("playlists","Playlists",Icons.AutoMirrored.Rounded.QueueMusic)).forEach { (path, label, icon) ->
                        val selected = route == path
                        val scale by animateFloatAsState(if(selected) 1.13f else 1f, spring(dampingRatio = .65f, stiffness = 440f), label = "Tab icon")
                        val tint by animateColorAsState(if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, tween(250), label = "Tab color")
                        Column(Modifier.weight(1f).heightIn(min = 60.dp).selectable(selected, role = Role.Tab, onClick = { onNavigate(path) }).padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(icon, null, Modifier.size(23.dp).graphicsLayer { scaleX = scale; scaleY = scale }, tint = tint)
                            Text(label, color = tint, style = MaterialTheme.typography.labelSmall, fontWeight = if(selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
