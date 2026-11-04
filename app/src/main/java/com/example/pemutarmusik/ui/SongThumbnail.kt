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
    // Dibatasi berdasarkan ukuran BYTE (1/8 heap), bukan jumlah item
    val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtLeast(4 * 1024 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
}

@Composable
fun SongThumbnail(song: MediaSong?, modifier: Modifier = Modifier, sizePx: Int = 150) {
    if (song == null) {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val context = LocalContext.current
    val cacheKey = "${song.id}_$sizePx"
    var bitmap by remember(cacheKey) { mutableStateOf(ThumbnailCache.cache.get(cacheKey)) }
    var loadAttempted by remember(cacheKey) { mutableStateOf(bitmap != null) }

    LaunchedEffect(cacheKey) {
        // Ambil gambar LANGSUNG dari file lagu aslinya (akurat 100%)
        if (bitmap == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            withContext(Dispatchers.IO) {
                try {
                    val thumb = context.contentResolver.loadThumbnail(song.uri, Size(sizePx, sizePx), null)
                    ThumbnailCache.cache.put(cacheKey, thumb)
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
