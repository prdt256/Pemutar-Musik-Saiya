package com.example.pemutarmusik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.pemutarmusik.data.MediaSong
import com.example.pemutarmusik.data.RepeatMode

@Composable
fun FullPlayer(
    song: MediaSong?,
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    playbackSpeed: Float,
    volume: Float,
    timerActive: Boolean,
    timerRemainingMs: Long,
    stopAfterCurrent: Boolean,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetVolume: (Float) -> Unit,
    onStartTimer: (Int) -> Unit,
    onStopAfterCurrent: () -> Unit,
    onCancelTimer: () -> Unit
) {
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showTimerMenu by remember { mutableStateOf(false) }
    var showVolumeSlider by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            if (dragAmount < -100) onNext()
                            else if (dragAmount > 100) onPrev()
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.KeyboardArrowDown, "Tutup",
                            tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Lagu", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("  |  ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                    Text("Lirik", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.MoreVert, "Menu", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Album Art
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(song?.albumArtUri).crossfade(true).build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = {
                            Icon(Icons.Default.MusicNote, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(80.dp))
                        }
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Title + Artist
                Text(
                    text = song?.title ?: "Tidak ada lagu",
                    color = MaterialTheme.colorScheme.onSurface, fontSize = 19.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = song?.artist ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                // Seekbar
                Slider(
                    value = currentPosition.toFloat(),
                    onValueChange = { onSeek(it.toInt()) },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(currentPosition), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    if (timerActive) {
                        Text("⏱ ${formatTime(timerRemainingMs.toInt())}", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                    Text(formatTime(duration), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }

                Spacer(Modifier.height(8.dp))

                // Main Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onToggleShuffle) {
                        Icon(Icons.Default.Shuffle, "Shuffle", modifier = Modifier.size(24.dp),
                            tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onPrev, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Default.SkipPrevious, "Previous",
                            tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(34.dp))
                    }
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier.size(60.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.background, modifier = Modifier.size(34.dp)
                        )
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Default.SkipNext, "Next",
                            tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(34.dp))
                    }
                    IconButton(onClick = onToggleRepeat) {
                        Icon(
                            when (repeatMode) { RepeatMode.ONE -> Icons.Default.RepeatOne; else -> Icons.Default.Repeat },
                            "Repeat", modifier = Modifier.size(24.dp),
                            tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Bottom Row: Speed, Volume, Timer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Speed
                    Box {
                        TextButton(onClick = { showSpeedMenu = true }) {
                            Text("${playbackSpeed}x", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                        DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                DropdownMenuItem(
                                    text = { Text("${speed}x", fontWeight = if (speed == playbackSpeed) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = { onSetSpeed(speed); showSpeedMenu = false }
                                )
                            }
                        }
                    }

                    // Volume
                    IconButton(onClick = { showVolumeSlider = !showVolumeSlider }) {
                        Icon(
                            if (volume > 0.5f) Icons.Default.VolumeUp
                            else if (volume > 0f) Icons.Default.VolumeDown
                            else Icons.Default.VolumeMute,
                            "Volume", tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Timer
                    Box {
                        IconButton(onClick = { showTimerMenu = true }) {
                            Icon(Icons.Default.Timer, "Timer",
                                tint = if (timerActive || stopAfterCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropdownMenu(expanded = showTimerMenu, onDismissRequest = { showTimerMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Setelah lagu ini",
                                    fontWeight = if (stopAfterCurrent) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { onStopAfterCurrent(); showTimerMenu = false }
                            )
                            listOf(15, 30, 45, 60).forEach { min ->
                                DropdownMenuItem(
                                    text = { Text("$min menit") },
                                    onClick = { onStartTimer(min); showTimerMenu = false }
                                )
                            }
                            if (timerActive) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Batalkan Timer", color = MaterialTheme.colorScheme.error) },
                                    onClick = { onCancelTimer(); showTimerMenu = false }
                                )
                            }
                        }
                    }
                }

                // Volume Slider
                if (showVolumeSlider) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VolumeMute, null, modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = volume, onValueChange = onSetVolume,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Icon(Icons.Default.VolumeUp, null, modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun formatTime(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}