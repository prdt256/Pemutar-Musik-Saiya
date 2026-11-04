package com.example.pemutarmusik

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import org.json.JSONArray
import java.io.File
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

/** Ambil semua lagu dari MediaStore (tanpa sort). Sort dilakukan terpisah lewat [sortSongs]. */
fun loadAllSongs(resolver: ContentResolver): List<MediaSong> {
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
    val result = ArrayList<MediaSong>()

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

    return result
}

/** Sort di memori memakai key yang sudah dihitung di [MediaSong] (tanpa alokasi string per perbandingan). */
fun sortSongs(songs: List<MediaSong>, sortBy: SortBy, order: SortOrder): List<MediaSong> {
    val cmp: Comparator<MediaSong> = when (sortBy) {
        SortBy.TITLE      -> compareBy<MediaSong> { it.sortTitle }
        SortBy.ARTIST     -> compareBy<MediaSong> { it.sortArtist }.thenBy { it.sortTitle }
        SortBy.DURATION   -> compareBy<MediaSong> { it.durationMs }
        SortBy.DATE_ADDED -> compareBy<MediaSong> { it.dateAdded }
    }
    return songs.sortedWith(if (order == SortOrder.ASC) cmp else cmp.reversed())
}

private val ART_BASE = Uri.parse("content://media/external/audio/albumart")
private val AUDIO_URI: Uri get() = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

/** Cache daftar lagu terakhir ke file, supaya app bisa menampilkan list seketika saat dibuka. */
object SongCache {
    private fun file(context: Context) = File(context.filesDir, "songs_cache_v1.json")

    fun save(context: Context, songs: List<MediaSong>) {
        try {
            val arr = JSONArray()
            for (s in songs) arr.put(JSONArray().apply {
                put(s.id); put(s.title); put(s.artist); put(s.durationMs)
                put(s.albumId); put(s.folderPath); put(s.dateAdded)
            })
            val tmp = File(context.filesDir, "songs_cache_v1.tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(file(context))
        } catch (_: Exception) { }
    }

    fun load(context: Context): List<MediaSong> {
        return try {
            val f = file(context)
            if (!f.exists()) return emptyList()
            val arr = JSONArray(f.readText())
            val out = ArrayList<MediaSong>(arr.length())
            for (i in 0 until arr.length()) {
                val a = arr.getJSONArray(i)
                val id = a.getLong(0)
                val albumId = a.getLong(4)
                out.add(MediaSong(
                    id = id, title = a.getString(1), artist = a.getString(2),
                    durationMs = a.getLong(3),
                    uri = ContentUris.withAppendedId(AUDIO_URI, id),
                    albumId = albumId,
                    albumArtUri = if (albumId > 0) ContentUris.withAppendedId(ART_BASE, albumId) else null,
                    folderPath = a.getString(5), dateAdded = a.getLong(6)
                ))
            }
            out
        } catch (_: Exception) { emptyList() }
    }
}
