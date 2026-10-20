package com.pulsify.music.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pulsify.music.domain.SUPPORT_URL

fun openSupportPage(context: Context, onError: (String) -> Unit) {
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_URL))) }
    catch(_: ActivityNotFoundException) { onError("No browser is available to open Buy Me a Coffee.") }
    catch(_: SecurityException) { onError("Your device could not open Buy Me a Coffee.") }
}

@Composable fun SupportCard(onSupport: () -> Unit, onDismiss: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Coffee, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Enjoying Pulsify?", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onSupport, contentPadding = PaddingValues(0.dp)) { Text("Buy bunnyfrost a coffee") }
            }
            ActionIcon(Icons.Rounded.Close, "Dismiss support reminder", onClick = onDismiss)
        }
    }
}
