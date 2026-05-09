package com.example.pemutarmusik.data

import android.net.Uri
import java.util.concurrent.TimeUnit

data class MediaSong(
    val id: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val uri: Uri,
    val albumId: Long = 0L,
    val albumArtUri: Uri? = null,
    val folderPath: String = "",
    val dateAdded: Long = 0L
) {
    val durationText: String
        get() {
            val m = TimeUnit.MILLISECONDS.toMinutes(durationMs)
            val s = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
            return "%d:%02d".format(m, s)
        }
}

enum class SortBy { TITLE, DURATION, DATE_ADDED }
enum class SortOrder { ASC, DESC }
enum class RepeatMode { OFF, ONE, ALL }