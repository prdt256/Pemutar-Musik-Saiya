package com.example.pemutarmusik

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.pemutarmusik.data.MediaSong
import com.example.pemutarmusik.data.SortBy
import com.example.pemutarmusik.data.SortOrder

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
        MediaStore.Audio.Media.ALBUM // Ambil nama album juga
    )
    @Suppress("DEPRECATION")
    projection.add(MediaStore.Audio.Media.DATA)

    val selection = "${MediaStore.Audio.Media.IS_MUSIC}=1"
    val sortColumn = when (sortBy) {
        SortBy.TITLE -> MediaStore.Audio.Media.TITLE
        SortBy.DURATION -> MediaStore.Audio.Media.DURATION
        SortBy.DATE_ADDED -> MediaStore.Audio.Media.DATE_ADDED
    }
    val sortOrderText = if (order == SortOrder.ASC) "ASC" else "DESC"
    val albumArtBaseUri = Uri.parse("content://media/external/audio/albumart")
    val result = mutableListOf<MediaSong>()

    resolver.query(collection, projection.toTypedArray(), selection, null,
        "$sortColumn $sortOrderText")?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
        @Suppress("DEPRECATION")
        val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val albumId = cursor.getLong(albumIdCol)
            
            // Logic unik: Jika albumId adalah 0 atau -1, 
            // MediaStore mungkin salah grouping.
            
            val folderPath = if (dataCol >= 0) {
                (cursor.getString(dataCol) ?: "").substringBeforeLast("/", "")
            } else ""

            result.add(MediaSong(
                id = id,
                title = cursor.getString(titleCol) ?: "Tidak diketahui",
                artist = cursor.getString(artistCol) ?: "Tidak diketahui",
                durationMs = cursor.getLong(durCol),
                uri = ContentUris.withAppendedId(collection, id),
                albumId = albumId,
                albumArtUri = if (albumId > 0) ContentUris.withAppendedId(albumArtBaseUri, albumId) else null,
                folderPath = folderPath,
                dateAdded = cursor.getLong(dateCol)
            ))
        }
    }
    return result
}