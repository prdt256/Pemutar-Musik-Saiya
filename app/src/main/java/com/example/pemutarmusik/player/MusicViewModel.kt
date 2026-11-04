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
import com.example.pemutarmusik.SongCache
import com.example.pemutarmusik.loadAllSongs
import com.example.pemutarmusik.sortSongs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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
        private set
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

    // Query yang benar-benar dipakai untuk filter (di-debounce supaya tidak filter tiap ketukan)
    private var appliedQuery by mutableStateOf("")

    // derivedStateOf: hanya dihitung ulang kalau songs / excludedFolders / appliedQuery berubah
    val filteredSongs: List<MediaSong> by derivedStateOf {
        var result = songs
        if (excludedFolders.isNotEmpty()) {
            val ex = excludedFolders.toHashSet()
            result = result.filter { it.folderPath !in ex }
        }
        if (appliedQuery.isNotBlank()) {
            val q = appliedQuery.trim().lowercase()
            result = result.filter { it.searchKey.contains(q) }
        }
        result
    }

    // Auto playlists
    val recentlyAdded: List<MediaSong> by derivedStateOf {
        val oneWeekAgo = System.currentTimeMillis() / 1000 - 7 * 24 * 3600
        songs.filter { it.dateAdded >= oneWeekAgo }.sortedByDescending { it.dateAdded }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var songHistory = mutableStateListOf<MediaSong>()

    // Di-cache: tidak lagi O(N) tiap recomposition (posisi lagu update tiap 250ms)
    // Urutan acak yang konsisten (dipakai untuk next/prev/swipe saat shuffle aktif)
    private var shuffleOrder by mutableStateOf<List<MediaSong>>(emptyList())

    /** Urutan putar efektif: urutan daftar biasa, atau urutan acak kalau shuffle aktif. */
    val playbackQueue: List<MediaSong> by derivedStateOf {
        val base = filteredSongs
        if (!shuffleEnabled) base
        else {
            val valid = base.associateBy { it.id }
            val ordered = shuffleOrder.mapNotNull { valid[it.id] }
            if (ordered.size == base.size) ordered
            else {
                val seen = ordered.map { it.id }.toHashSet()
                ordered + base.filter { it.id !in seen }
            }
        }
    }

    val currentIndex: Int by derivedStateOf {
        currentSong?.let { s -> playbackQueue.indexOfFirst { it.id == s.id } } ?: -1
    }

    /** Acak ulang; [first] (kalau ada) ditaruh paling depan supaya jadi lagu yang sedang/akan diputar. */
    private fun reshuffle(first: MediaSong?) {
        val rest = filteredSongs.filter { it.id != first?.id }.shuffled()
        shuffleOrder = if (first != null) listOf(first) + rest else rest
    }

    @OptIn(FlowPreview::class)
    private fun startObservers() {
        // Debounce pencarian
        viewModelScope.launch {
            snapshotFlow { searchQuery }.debounce(150).distinctUntilChanged()
                .collect { appliedQuery = it }
        }
        // Polling posisi HANYA saat sedang memutar
        viewModelScope.launch {
            snapshotFlow { isPlaying }.distinctUntilChanged().collectLatest { playing ->
                if (!playing) return@collectLatest
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
    }

    init {
        // Load saved excluded folders
        excludedFolders.addAll(prefs.getExcludedFolders())
        startObservers()
    }

    // Data mentah (belum disort) + kriteria sort aktif
    private var rawSongs: List<MediaSong> = emptyList()
    private var curSortBy = SortBy.TITLE
    private var curSortOrder = SortOrder.ASC
    private var loadJob: Job? = null
    private var sortJob: Job? = null

    private suspend fun publish(raw: List<MediaSong>) {
        val sorted = withContext(Dispatchers.Default) { sortSongs(raw, curSortBy, curSortOrder) }
        val folders = withContext(Dispatchers.Default) { raw.map { it.folderPath }.distinct().sorted() }
        songs = sorted
        allFolders = folders
    }

    /** Ganti kriteria sort: cukup sort ulang di memori, TANPA query MediaStore lagi. */
    fun setSort(sortBy: SortBy, sortOrder: SortOrder) {
        curSortBy = sortBy
        curSortOrder = sortOrder
        if (rawSongs.isEmpty()) return
        sortJob?.cancel()
        sortJob = viewModelScope.launch {
            songs = withContext(Dispatchers.Default) { sortSongs(rawSongs, sortBy, sortOrder) }
        }
    }

    /** Tampilkan cache seketika, lalu refresh dari MediaStore di background. */
    fun loadSongs(context: Context) {
        val app = context.applicationContext
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (rawSongs.isEmpty()) {
                val cached = withContext(Dispatchers.IO) { SongCache.load(app) }
                if (cached.isNotEmpty()) { rawSongs = cached; publish(cached) }
            }
            val fresh = withContext(Dispatchers.IO) { loadAllSongs(app.contentResolver) }
            if (fresh.map { it.id } != rawSongs.map { it.id } ||
                fresh.map { it.title } != rawSongs.map { it.title }) {
                rawSongs = fresh
                publish(fresh)
            }
            withContext(Dispatchers.IO) { SongCache.save(app, fresh) }
        }
    }

    fun toggleFolder(folderPath: String) {
        if (folderPath in excludedFolders) excludedFolders.remove(folderPath)
        else excludedFolders.add(folderPath)
        prefs.saveExcludedFolders(excludedFolders.toSet())
    }

    fun isFolderEnabled(folderPath: String) = folderPath !in excludedFolders

    /** Dipilih user dari daftar: kalau shuffle aktif, acak ulang dengan lagu ini di depan. */
    fun playFromList(context: Context, song: MediaSong) {
        if (shuffleEnabled) reshuffle(song)
        playSong(context, song)
    }

    /** Tombol "Acak" di daftar: aktifkan shuffle dan mulai dari lagu acak. */
    fun playShuffled(context: Context) {
        val pick = filteredSongs.randomOrNull() ?: return
        shuffleEnabled = true
        reshuffle(pick)
        playSong(context, pick)
    }

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
        val list = playbackQueue
        if (list.isEmpty()) return null
        val idx = currentIndex
        return when {
            idx < 0 -> list.first()
            idx < list.size - 1 -> list[idx + 1]
            repeatMode == RepeatMode.ALL -> {
                if (shuffleEnabled) { reshuffle(null); playbackQueue.firstOrNull() }
                else list.first()
            }
            else -> null
        }
    }

    private fun getPreviousSong(): MediaSong? {
        if (songHistory.isNotEmpty()) return songHistory.removeAt(songHistory.size - 1)
        val list = playbackQueue
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
        getPreviousSong()?.let { startPlayback(context, it) }
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        currentPosition = position
    }

    fun toggleShuffle() {
        if (!shuffleEnabled) reshuffle(currentSong)
        shuffleEnabled = !shuffleEnabled
    }

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
