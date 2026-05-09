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
            val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
            val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
            
            return if (hours > 0) {
                "%d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%d:%02d".format(minutes, seconds)
            }
        }
}

enum class SortBy { TITLE, DURATION, DATE_ADDED }
enum class SortOrder { ASC, DESC }
enum class RepeatMode { OFF, ONE, ALL }