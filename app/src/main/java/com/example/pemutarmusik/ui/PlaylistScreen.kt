package com.example.pemutarmusik.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pemutarmusik.data.MediaSong

@Composable
fun PlaylistScreen(
    recentlyAdded: List<MediaSong>,
    history: List<MediaSong>,
    favorites: List<MediaSong>,
    currentSong: MediaSong?,
    actions: SongMenuActions,
    onClearHistory: () -> Unit,
    onPlaySong: (MediaSong) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 160.dp)
    ) {
        item {
            Text(
                text = "Playlist Otomatis",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
        }

        playlistSection(
            prefix = "fav", title = "Favorit", icon = Icons.Default.Favorite,
            subtitle = "${favorites.size} lagu",
            emptyText = "Belum ada favorit. Pilih menu titik tiga pada lagu lalu \"Tambahkan ke favorit\".",
            songs = favorites, limit = Int.MAX_VALUE,
            currentSong = currentSong, actions = actions, onPlaySong = onPlaySong
        )

        playlistSection(
            prefix = "his", title = "Riwayat Putar", icon = Icons.Default.History,
            subtitle = "${history.size} lagu",
            emptyText = "Belum ada riwayat. Putar sebuah lagu dulu.",
            songs = history, limit = 50,
            currentSong = currentSong, actions = actions, onPlaySong = onPlaySong,
            trailingHeader = if (history.isNotEmpty()) ({
                TextButton(onClick = onClearHistory) { Text("Hapus riwayat") }
            }) else null
        )

        playlistSection(
            prefix = "new", title = "Baru Ditambahkan", icon = Icons.Default.Schedule,
            subtitle = "${recentlyAdded.size} lagu minggu ini",
            emptyText = "Tidak ada lagu baru minggu ini.",
            songs = recentlyAdded, limit = 20,
            currentSong = currentSong, actions = actions, onPlaySong = onPlaySong
        )

        // Placeholder: Playlist kustom
        item {
            Spacer(Modifier.height(24.dp))
            Text("Playlist Kustom", fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp))
        }
        item {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Segera hadir!", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Satu bagian playlist. Key diberi [prefix] supaya lagu yang sama di beberapa bagian tidak bentrok. */
private fun LazyListScope.playlistSection(
    prefix: String,
    title: String,
    icon: ImageVector,
    subtitle: String,
    emptyText: String,
    songs: List<MediaSong>,
    limit: Int,
    currentSong: MediaSong?,
    actions: SongMenuActions,
    onPlaySong: (MediaSong) -> Unit,
    trailingHeader: (@Composable () -> Unit)? = null
) {
    item(key = "${prefix}_header") {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                trailingHeader?.invoke()
            }
        }
    }
    if (songs.isEmpty()) {
        item(key = "${prefix}_empty") {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    } else {
        items(songs.take(limit), key = { "${prefix}_${it.id}" }) { song ->
            SongItem(song, song.id == currentSong?.id, actions) { onPlaySong(song) }
        }
    }
}
