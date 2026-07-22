package com.example.pemutarmusik

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.pemutarmusik.data.MediaSong
import com.example.pemutarmusik.data.SortBy
import com.example.pemutarmusik.data.SortOrder

/**
 * Membersihkan teks untuk pengurutan natural (case-insensitive, abaikan simbol & angka di awal).
 * Contoh: "(01) Lagu Saya" → "lagu saya", "#Party" → "party"
 */
fun cleanForSort(text: String): String {
    val t = text.trimStart()
    var startIndex = 0
    
    // Hapus angka dalam kurung di awal: (01), [02], dll
    if (t.isNotEmpty() && (t[0] == '[' || t[0] == '(' || t[0] == '{')) {
        val closing = when(t[0]) {
            '[' -> ']'
            '(' -> ')'
            else -> '}'
        }
        val closeIdx = t.indexOf(closing)
        if (closeIdx != -1) {
            var allDigits = true
            for (i in 1 until closeIdx) {
                if (!t[i].isDigit() && !t[i].isWhitespace()) {
                    allDigits = false
                    break
                }
            }
            if (allDigits) {
                startIndex = closeIdx + 1
            }
        }
    }
    
    // Hapus simbol non-huruf/non-angka di awal
    while (startIndex < t.length && !t[startIndex].isLetterOrDigit()) {
        startIndex++
    }
    
    if (startIndex >= t.length) return t.lowercase()
    return t.substring(startIndex).trimStart().lowercase()
}

fun loadAllSongs(
    resolver: ContentResolver,
    sortBy: SortBy = SortBy.TITLE,
    order: SortOrder = SortOrder.ASC
): List<MediaSong> {
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    val projection = mutableListOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.IS_MUSIC,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.ALBUM
    )
    @Suppress("DEPRECATION")
    projection.add(MediaStore.Audio.Media.DATA)

    val selection = "${MediaStore.Audio.Media.IS_MUSIC}=1"
    val albumArtBaseUri = Uri.parse("content://media/external/audio/albumart")
    val result = mutableListOf<MediaSong>()

    // Ambil semua lagu dari MediaStore tanpa ORDER BY (kita sort sendiri di Kotlin)
    resolver.query(collection, projection.toTypedArray(), selection, null, null)?.use { cursor ->
        val idCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val durCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val albumIdCol= cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val dateCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
        @Suppress("DEPRECATION")
        val dataCol   = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val albumId = cursor.getLong(albumIdCol)
            val folderPath = if (dataCol >= 0) {
                (cursor.getString(dataCol) ?: "").substringBeforeLast("/", "")
            } else ""

            result.add(MediaSong(
                id        = id,
                title     = cursor.getString(titleCol)  ?: "Tidak diketahui",
                artist    = cursor.getString(artistCol) ?: "Tidak diketahui",
                durationMs= cursor.getLong(durCol),
                uri       = ContentUris.withAppendedId(collection, id),
                albumId   = albumId,
                albumArtUri = if (albumId > 0)
                    ContentUris.withAppendedId(albumArtBaseUri, albumId) else null,
                folderPath  = folderPath,
                dateAdded   = cursor.getLong(dateCol)
            ))
        }
    }

    // ── Natural Sort di Kotlin ──────────────────────────────────────────────
    // Semua sort berbasis cleanForSort() agar simbol/angka di awal judul/artis
    // tidak mengganggu pengurutan alfabetis alami.
    return when (order) {
        SortOrder.ASC -> when (sortBy) {
            SortBy.TITLE      -> result.sortedWith(compareBy { cleanForSort(it.title) })
            SortBy.ARTIST     -> result.sortedWith(
                compareBy({ cleanForSort(it.artist) }, { cleanForSort(it.title) })
            )
            SortBy.DURATION   -> result.sortedBy { it.durationMs }
            SortBy.DATE_ADDED -> result.sortedBy { it.dateAdded }
        }
        SortOrder.DESC -> when (sortBy) {
            SortBy.TITLE      -> result.sortedWith(compareByDescending { cleanForSort(it.title) })
            SortBy.ARTIST     -> result.sortedWith(
                compareByDescending<MediaSong> { cleanForSort(it.artist) }
                    .thenBy { cleanForSort(it.title) }
            )
            SortBy.DURATION   -> result.sortedByDescending { it.durationMs }
            SortBy.DATE_ADDED -> result.sortedByDescending { it.dateAdded }
        }
    }
}