package com.example.pemutarmusik.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pemutarmusik.data.MediaSong

/** Panel (bottom sheet) berisi daftar lagu yang akan diputar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    current: MediaSong?,
    upNext: List<MediaSong>,
    upcoming: List<MediaSong>,
    shuffle: Boolean,
    onPlay: (MediaSong) -> Unit,
    onRemoveFromUpNext: (MediaSong) -> Unit,
    onClearUpNext: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
            Text(
                text = if (shuffle) "Antrean putar \u2022 Acak" else "Antrean putar",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                if (current != null) {
                    item(key = "hdr_now") { SectionLabel("Sedang diputar") }
                    item(key = "now") { QueueRow(current, highlight = true, onClick = {}) }
                }

                if (upNext.isNotEmpty()) {
                    item(key = "hdr_next") {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Diputar berikutnya", fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary, fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onClearUpNext) { Text("Kosongkan") }
                        }
                    }
                    items(upNext, key = { "u${it.id}" }) { song ->
                        QueueRow(song, onClick = { onPlay(song) }) {
                            IconButton(onClick = { onRemoveFromUpNext(song) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Close, "Hapus dari antrean",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                item(key = "hdr_upcoming") { SectionLabel("Selanjutnya") }
                if (upcoming.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "Tidak ada lagu selanjutnya",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                } else {
                    items(upcoming, key = { "q${it.id}" }) { song ->
                        QueueRow(song, onClick = { onPlay(song) })
                    }
                }
                item(key = "spacer") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun QueueRow(
    song: MediaSong,
    highlight: Boolean = false,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SongThumbnail(
            song = song,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
                color = if (highlight) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${song.artist} \u2022 ${song.durationText}", maxLines = 1,
                overflow = TextOverflow.Ellipsis, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing?.invoke()
    }
}
