package com.example.pemutarmusik.ui

import android.graphics.Bitmap
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.pemutarmusik.data.MediaSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Cache khusus biar gambar yang udah di-load gak usah mikir lagi pas di-scroll
object ThumbnailCache {
    val cache = LruCache<Long, Bitmap>(100)
}

@Composable
fun SongThumbnail(song: MediaSong?, modifier: Modifier = Modifier) {
    if (song == null) {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val context = LocalContext.current
    var bitmap by remember(song.id) { mutableStateOf(ThumbnailCache.cache.get(song.id)) }
    var loadAttempted by remember(song.id) { mutableStateOf(bitmap != null) }

    LaunchedEffect(song.id) {
        // Ambil gambar LANGSUNG dari file lagu aslinya (akurat 100%)
        if (bitmap == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            withContext(Dispatchers.IO) {
                try {
                    val thumb = context.contentResolver.loadThumbnail(song.uri, Size(300, 300), null)
                    ThumbnailCache.cache.put(song.id, thumb)
                    bitmap = thumb
                } catch (e: Exception) {
                    // Kalo emang file lagunya gak ada gambar
                } finally {
                    loadAttempted = true
                }
            }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            loadAttempted = true
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else if (loadAttempted && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        // Fallback untuk HP Android jadul
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(song.albumArtUri)
                .crossfade(false)
                .build(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
        )
    } else if (loadAttempted) {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
