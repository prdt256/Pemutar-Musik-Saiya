package com.example.pemutarmusik.player

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pemutarmusik.data.MediaSong
import com.example.pemutarmusik.data.PrefsManager
import com.example.pemutarmusik.data.RepeatMode
import com.example.pemutarmusik.data.SortBy
import com.example.pemutarmusik.data.SortOrder
import com.example.pemutarmusik.loadAllSongs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PrefsManager(application)
    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    var songs by mutableStateOf<List<MediaSong>>(emptyList())
    var currentSong by mutableStateOf<MediaSong?>(null)
    var isPlaying by mutableStateOf(false)
    var currentPosition by mutableIntStateOf(0)
    var duration by mutableIntStateOf(0)
    var shuffleEnabled by mutableStateOf(false)
    var repeatMode by mutableStateOf(RepeatMode.OFF)
    var searchQuery by mutableStateOf("")

    // Folder management
    var allFolders by mutableStateOf<List<String>>(emptyList())
    var excludedFolders = mutableStateListOf<String>()

    // Playback speed & volume (volume disinkronkan dari sistem AudioManager)
    var playbackSpeed by mutableFloatStateOf(1.0f)
    var playerVolume by mutableFloatStateOf(
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
    )

    // Sleep timer
    var timerActive by mutableStateOf(false)
    var timerRemainingMs by mutableLongStateOf(0L)
    var stopAfterCurrent by mutableStateOf(false)
    private var timerJob: Job? = null

    val filteredSongs: List<MediaSong>
        get() {
            var result = songs
            if (excludedFolders.isNotEmpty()) {
                result = result.filter { it.folderPath !in excludedFolders }
            }
            if (searchQuery.isNotBlank()) {
                val q = searchQuery.lowercase()
                result = result.filter {
                    it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
                }
            }
            return result
        }

    // Auto playlists
    val recentlyAdded: List<MediaSong>
        get() {
            val oneWeekAgo = System.currentTimeMillis() / 1000 - 7 * 24 * 3600
            return songs.filter { it.dateAdded >= oneWeekAgo }
                .sortedByDescending { it.dateAdded }
        }

    private var mediaPlayer: MediaPlayer? = null
    private var songHistory = mutableStateListOf<MediaSong>()

    val currentIndex: Int
        get() = currentSong?.let { s -> filteredSongs.indexOfFirst { it.id == s.id } } ?: -1

    init {
        // Load saved excluded folders
        excludedFolders.addAll(prefs.getExcludedFolders())

        viewModelScope.launch {
            while (true) {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        currentPosition = it.currentPosition
                        duration = it.duration.coerceAtLeast(0)
                    }
                }
                delay(250)
            }
        }
    }

    fun loadSongs(context: Context, sortBy: SortBy, sortOrder: SortOrder) {
        viewModelScope.launch(Dispatchers.IO) {
            val loadedSongs = loadAllSongs(context.contentResolver, sortBy, sortOrder)
            val folders = loadedSongs.map { it.folderPath }.distinct().sorted()
            withContext(Dispatchers.Main) {
                songs = loadedSongs
                allFolders = folders
            }
        }
    }

    fun toggleFolder(folderPath: String) {
        if (folderPath in excludedFolders) excludedFolders.remove(folderPath)
        else excludedFolders.add(folderPath)
        prefs.saveExcludedFolders(excludedFolders.toSet())
    }

    fun isFolderEnabled(folderPath: String) = folderPath !in excludedFolders

    fun playSong(context: Context, song: MediaSong) {
        currentSong?.let { songHistory.add(it) }
        startPlayback(context, song)
    }

    private fun startPlayback(context: Context, song: MediaSong) {
        currentSong = song
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(context, song.uri)?.apply {
            setOnCompletionListener { onSongCompleted(context) }
            if (playbackSpeed != 1.0f) {
                playbackParams = PlaybackParams().setSpeed(playbackSpeed)
            }
            // Volume dikontrol oleh AudioManager (sistem HP), bukan MediaPlayer internal
            start()
        }
        isPlaying = mediaPlayer != null
    }

    private fun onSongCompleted(context: Context) {
        if (stopAfterCurrent) {
            stopAfterCurrent = false
            isPlaying = false
            return
        }
        when (repeatMode) {
            RepeatMode.ONE -> currentSong?.let { startPlayback(context, it) }
            RepeatMode.ALL -> getNextSong()?.let { startPlayback(context, it) }
            RepeatMode.OFF -> {
                val next = getNextSong()
                if (next != null) startPlayback(context, next)
                else isPlaying = false
            }
        }
    }

    private fun getNextSong(): MediaSong? {
        val list = filteredSongs
        if (list.isEmpty()) return null
        return if (shuffleEnabled) {
            list.filter { it.id != currentSong?.id }.randomOrNull() ?: list.randomOrNull()
        } else {
            val idx = currentIndex
            if (idx < 0) list.firstOrNull()
            else if (idx < list.size - 1) list[idx + 1]
            else if (repeatMode == RepeatMode.ALL) list.firstOrNull()
            else null
        }
    }

    private fun getPreviousSong(): MediaSong? {
        if (songHistory.isNotEmpty()) return songHistory.removeAt(songHistory.size - 1)
        val list = filteredSongs
        val idx = currentIndex
        return if (idx > 0) list[idx - 1]
        else if (repeatMode == RepeatMode.ALL) list.lastOrNull()
        else null
    }

    fun togglePlay() {
        mediaPlayer?.let {
            if (it.isPlaying) { it.pause(); isPlaying = false }
            else { it.start(); isPlaying = true }
        }
    }

    fun playNext(context: Context) {
        currentSong?.let { songHistory.add(it) }
        getNextSong()?.let { startPlayback(context, it) }
    }

    fun playPrevious(context: Context) {
        if (currentPosition > 3000) { seekTo(0); return }
        getPreviousSong()?.let { startPlayback(context, it) }
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        currentPosition = position
    }

    fun toggleShuffle() { shuffleEnabled = !shuffleEnabled }

    fun toggleRepeat() {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    fun setSpeed(speed: Float) {
        playbackSpeed = speed
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.playbackParams = PlaybackParams().setSpeed(speed)
            }
        }
    }

    fun updateVolume(vol: Float) {
        playerVolume = vol
        // Atur volume stream media sistem Android secara real-time
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            (vol * maxVol).roundToInt().coerceIn(0, maxVol),
            0 // tanpa UI indicator dari sistem
        )
    }

    // Sleep timer
    fun startTimer(minutes: Int) {
        cancelTimer()
        timerRemainingMs = minutes * 60 * 1000L
        timerActive = true
        timerJob = viewModelScope.launch {
            while (timerRemainingMs > 0) {
                delay(1000)
                timerRemainingMs -= 1000
            }
            mediaPlayer?.pause()
            isPlaying = false
            timerActive = false
        }
    }

    fun setStopAfterCurrent() {
        stopAfterCurrent = !stopAfterCurrent
    }

    fun cancelTimer() {
        timerJob?.cancel()
        timerActive = false
        timerRemainingMs = 0
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        currentSong = null
        currentPosition = 0
        duration = 0
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
