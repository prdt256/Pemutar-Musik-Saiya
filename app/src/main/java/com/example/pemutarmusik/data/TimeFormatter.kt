package com.example.pemutarmusik.data

fun formatDuration(duration: Long): String {

    val minutes = duration / 1000 / 60
    val seconds = (duration / 1000) % 60

    return "%02d:%02d".format(minutes, seconds)
}