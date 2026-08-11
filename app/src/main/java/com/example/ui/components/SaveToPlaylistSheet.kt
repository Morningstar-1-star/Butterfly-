package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.UserPlaylist
import com.example.model.VideoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveToPlaylistSheet(
    video: VideoItem,
    playlists: List<UserPlaylist>,
    onAddToPlaylist: (playlistId: String, video: VideoItem) -> Unit,
    onRemoveFromPlaylist: (playlistId: String, video: VideoItem) -> Unit,
    onCreatePlaylist: (title: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistTitle by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Save video to...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (playlists.isEmpty()) {
                Text(
                    text = "No custom playlists created yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(playlists) { playlist ->
                        val isInPlaylist = remember(playlist.videos, video.id) {
                            playlist.videos.any { it.id == video.id }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isInPlaylist) {
                                        onRemoveFromPlaylist(playlist.id, video)
                                        Toast.makeText(context, "Removed from ${playlist.title}", Toast.LENGTH_SHORT).show()
                                    } else {
                                        onAddToPlaylist(playlist.id, video)
                                        Toast.makeText(context, "Added to ${playlist.title}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Checkbox(
                                    checked = isInPlaylist,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            onAddToPlaylist(playlist.id, video)
                                            Toast.makeText(context, "Added to ${playlist.title}", Toast.LENGTH_SHORT).show()
                                        } else {
                                            onRemoveFromPlaylist(playlist.id, video)
                                            Toast.makeText(context, "Removed from ${playlist.title}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                                Text(
                                    text = playlist.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "${playlist.videos.size} videos",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { showNewPlaylistDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "New Playlist")
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Playlist")
            }
        }
    }

    if (showNewPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showNewPlaylistDialog = false },
            title = { Text("Create New Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistTitle,
                    onValueChange = { newPlaylistTitle = it },
                    label = { Text("Playlist Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val title = newPlaylistTitle.trim()
                        if (title.isNotBlank()) {
                            onCreatePlaylist(title)
                            Toast.makeText(context, "Playlist created: $title", Toast.LENGTH_SHORT).show()
                            newPlaylistTitle = ""
                            showNewPlaylistDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewPlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
