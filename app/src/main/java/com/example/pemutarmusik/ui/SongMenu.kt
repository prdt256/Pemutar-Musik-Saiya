package com.example.pemutarmusik.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.pemutarmusik.data.MediaSong

/** Aksi yang tersedia di menu titik tiga sebuah lagu. */
class SongMenuActions(
    val isFavorite: (MediaSong) -> Boolean,
    val isQueued: (MediaSong) -> Boolean,
    val onPlayNext: (MediaSong) -> Unit,
    val onRemoveNext: (MediaSong) -> Unit,
    val onToggleFavorite: (MediaSong) -> Unit,
    val onDelete: (MediaSong) -> Unit
)

@Composable
fun SongMenuButton(
    song: MediaSong,
    actions: SongMenuActions,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 32.dp,
    iconSize: Dp = 18.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = modifier.size(buttonSize)) {
            Icon(Icons.Default.MoreVert, "Menu", tint = tint, modifier = Modifier.size(iconSize))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Putar berikutnya") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                onClick = { open = false; actions.onPlayNext(song) }
            )
            if (actions.isQueued(song)) {
                DropdownMenuItem(
                    text = { Text("Hapus dari antrean berikutnya") },
                    leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, null) },
                    onClick = { open = false; actions.onRemoveNext(song) }
                )
            }
            val fav = actions.isFavorite(song)
            DropdownMenuItem(
                text = { Text(if (fav) "Hapus dari favorit" else "Tambahkan ke favorit") },
                leadingIcon = {
                    Icon(
                        if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null,
                        tint = if (fav) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = { open = false; actions.onToggleFavorite(song) }
            )
            DropdownMenuItem(
                text = { Text("Hapus lagu", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                },
                onClick = { open = false; actions.onDelete(song) }
            )
        }
    }
}
