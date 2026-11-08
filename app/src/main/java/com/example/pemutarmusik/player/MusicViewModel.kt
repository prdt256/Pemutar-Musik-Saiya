package com.example.pemutarmusik.player

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.widget.Toast
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

    // ── Favorit, riwayat, dan antrean "putar berikutnya" ──────────────────
    val favoriteIds = mutableStateListOf<Long>()
    val historyIds = mutableStateListOf<Long>()
    val upNext = mutableStateListOf<MediaSong>()
    // Lagu acuan untuk melanjutkan urutan setelah antrean "putar berikutnya" habis
    private var anchor by mutableStateOf<MediaSong?>(null)

    val favoriteSongs: List<MediaSong> by derivedStateOf {
        val m = songs.associateBy { it.id }
        favoriteIds.mapNotNull { m[it] }
    }
    val historySongs: List<MediaSong> by derivedStateOf {
        val m = songs.associateBy { it.id }
        historyIds.mapNotNull { m[it] }
    }

    private var lastToast: Toast? = null
    /** Satu Toast saja: yang lama dibatalkan supaya pesan tidak menumpuk. */
    fun toast(message: String) {
        lastToast?.cancel()
        lastToast = Toast.makeText(getApplication<Application>(), message, Toast.LENGTH_SHORT)
            .also { it.show() }
    }

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

    /** Lagu yang akan diputar setelah lagu acuan (tanpa lagu yang sedang/akan diputar). */
    val upcoming: List<MediaSong> by derivedStateOf {
        val q = playbackQueue
        val refId = anchor?.id ?: currentSong?.id
        val i = q.indexOfFirst { it.id == refId }
        if (i < 0) emptyList() else q.drop(i + 1)
    }

    /** Acak semua lagu. Lagu yang sedang diputar ikut teracak posisinya, jadi prev dan next sama-sama ada. */
    private fun reshuffle() {
        shuffleOrder = filteredSongs.shuffled()
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
        favoriteIds.addAll(prefs.getFavorites())
        historyIds.addAll(prefs.getHistory())
        startObservers()
    }

    // Data mentah (belum disort) + kriteria sort aktif
    private var rawSongs: List<MediaSong> = emptyList()
    private var curSortBy = SortBy.TITLE
    private var curSortOrder = SortOrder.ASC
    private var loadJob: Job? = null
    private var sortJob: Job? = null

    private var lastSongRestored = false

    /**
     * Saat app dibuka, tampilkan lagu terakhir yang diputar di mini player (dalam keadaan berhenti).
     * Hanya sekali per peluncuran, jadi menutup mini player dengan X tidak memunculkannya lagi.
     */
    private fun restoreLastSong() {
        if (lastSongRestored || songs.isEmpty()) return
        lastSongRestored = true
        if (currentSong != null) return
        val lastId = historyIds.firstOrNull() ?: return
        songs.firstOrNull { it.id == lastId }?.let {
            currentSong = it
            isPlaying = false
            currentPosition = 0
            duration = it.durationMs.toInt()
        }
    }

    private suspend fun publish(raw: List<MediaSong>) {
        val sorted = withContext(Dispatchers.Default) { sortSongs(raw, curSortBy, curSortOrder) }
        val folders = withContext(Dispatchers.Default) { raw.map { it.folderPath }.distinct().sorted() }
        songs = sorted
        allFolders = folders
        restoreLastSong()
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

    /** Dipilih user dari daftar. Urutan acak (kalau aktif) dipertahankan. */
    fun playFromList(context: Context, song: MediaSong) {
        playSong(context, song)
    }

    /** Tombol "Acak" di daftar: aktifkan shuffle dan mulai dari lagu acak. */
    fun playShuffled(context: Context) {
        if (filteredSongs.isEmpty()) return
        shuffleEnabled = true
        reshuffle()
        val pick = playbackQueue.randomOrNull() ?: return
        playSong(context, pick)
        toast("Acak: aktif")
    }

    fun playSong(context: Context, song: MediaSong) {
        startPlayback(context, song)
    }

    private fun recordHistory(id: Long) {
        historyIds.remove(id)
        historyIds.add(0, id)
        while (historyIds.size > 100) historyIds.removeAt(historyIds.lastIndex)
        prefs.saveHistory(historyIds)
    }

    fun clearHistory() {
        historyIds.clear()
        prefs.saveHistory(historyIds)
        toast("Riwayat dihapus")
    }

    private fun startPlayback(context: Context, song: MediaSong) {
        // Lagu dari antrean "putar berikutnya": ingat posisi semula supaya urutan lanjut dari sana
        val fromQueue = upNext.firstOrNull()?.id == song.id
        if (fromQueue) { if (anchor == null) anchor = currentSong } else anchor = null
        upNext.removeAll { it.id == song.id }
        recordHistory(song.id)

        currentSong = song
        currentPosition = 0
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
            toast("Berhenti setelah lagu ini")
            return
        }
        when (repeatMode) {
            RepeatMode.ONE -> currentSong?.let { startPlayback(context, it) }
            RepeatMode.ALL -> nextTarget(manual = false)?.let { startPlayback(context, it) }
            RepeatMode.OFF -> {
                val next = nextTarget(manual = false)
                if (next != null) startPlayback(context, next)
                else isPlaying = false
            }
        }
    }

    /** Lagu berikutnya. [manual] = true untuk tombol/gestur next (di mode acak tetap berputar di ujung). */
    private fun nextTarget(manual: Boolean): MediaSong? {
        upNext.firstOrNull()?.let { return it }
        val list = playbackQueue
        if (list.isEmpty()) return null
        val refId = anchor?.id ?: currentSong?.id
        val idx = list.indexOfFirst { it.id == refId }
        if (idx < 0) return list.first()
        if (idx < list.size - 1) return list[idx + 1]
        val wrap = repeatMode == RepeatMode.ALL || (manual && shuffleEnabled)
        if (!wrap) return null
        if (shuffleEnabled) {
            reshuffle()
            val q = playbackQueue
            return q.firstOrNull { it.id != currentSong?.id } ?: q.firstOrNull()
        }
        return list.first()
    }

    /** Lagu sebelumnya menurut urutan putar (acak kalau shuffle aktif). Di ujung awal berputar ke akhir. */
    private fun prevTarget(): MediaSong? {
        val list = playbackQueue
        val idx = currentIndex
        return when {
            list.isEmpty() || idx < 0 -> null
            idx > 0 -> list[idx - 1]
            shuffleEnabled || repeatMode == RepeatMode.ALL -> list.last()
            else -> null
        }
    }

    fun togglePlay() {
        val player = mediaPlayer
        if (player != null) {
            if (player.isPlaying) { player.pause(); isPlaying = false }
            else { player.start(); isPlaying = true }
        } else {
            // Belum ada player (mis. lagu terakhir yang dipulihkan saat app dibuka): mulai dari awal
            currentSong?.let { startPlayback(getApplication<Application>(), it) }
        }
    }

    fun playNext(context: Context) {
        nextTarget(manual = true)?.let { startPlayback(context, it) }
    }

    fun playPrevious(context: Context) {
        prevTarget()?.let { startPlayback(context, it) }
    }

    // ── Antrean "putar berikutnya" ────────────────────────────────────────
    fun queueNext(song: MediaSong) {
        if (currentSong == null) { toast("Putar sebuah lagu dulu"); return }
        upNext.removeAll { it.id == song.id }
        upNext.add(song)
        toast("Diputar berikutnya: ${song.title}")
    }

    fun removeFromUpNext(song: MediaSong) {
        if (upNext.removeAll { it.id == song.id }) toast("Dihapus dari antrean berikutnya")
    }

    fun clearUpNext() {
        if (upNext.isNotEmpty()) { upNext.clear(); toast("Antrean berikutnya dikosongkan") }
    }

    fun isQueued(song: MediaSong) = upNext.any { it.id == song.id }

    // ── Favorit ───────────────────────────────────────────────────────────
    fun isFavorite(song: MediaSong) = song.id in favoriteIds

    fun toggleFavorite(song: MediaSong) {
        if (song.id in favoriteIds) {
            favoriteIds.remove(song.id)
            toast("Dihapus dari favorit")
        } else {
            favoriteIds.add(0, song.id)
            toast("Ditambahkan ke favorit")
        }
        prefs.saveFavorites(favoriteIds)
    }

    // ── Hapus lagu (setelah file benar-benar terhapus oleh sistem) ────────
    fun onSongDeleted(song: MediaSong) {
        val app = getApplication<Application>()
        val wasCurrent = currentSong?.id == song.id
        val next = if (wasCurrent) nextTarget(manual = true)?.takeIf { it.id != song.id } else null

        rawSongs = rawSongs.filter { it.id != song.id }
        songs = songs.filter { it.id != song.id }
        allFolders = rawSongs.map { it.folderPath }.distinct().sorted()
        upNext.removeAll { it.id == song.id }
        if (anchor?.id == song.id) anchor = null
        favoriteIds.remove(song.id); prefs.saveFavorites(favoriteIds)
        historyIds.remove(song.id); prefs.saveHistory(historyIds)

        if (wasCurrent) {
            if (next != null) startPlayback(app, next) else stop()
        }
        val snapshot = rawSongs
        viewModelScope.launch(Dispatchers.IO) { SongCache.save(app, snapshot) }
        toast("Lagu dihapus")
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        currentPosition = position
    }

    fun toggleShuffle() {
        if (!shuffleEnabled) reshuffle()
        shuffleEnabled = !shuffleEnabled
        toast(if (shuffleEnabled) "Acak: aktif" else "Acak: mati")
    }

    fun toggleRepeat() {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        toast(when (repeatMode) {
            RepeatMode.OFF -> "Ulangi: mati"
            RepeatMode.ALL -> "Ulangi semua lagu"
            RepeatMode.ONE -> "Ulangi satu lagu"
        })
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
        clearTimer()
        toast("Timer tidur: $minutes menit")
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
            toast("Timer tidur selesai, musik dijeda")
        }
    }

    fun setStopAfterCurrent() {
        stopAfterCurrent = !stopAfterCurrent
        toast(if (stopAfterCurrent) "Berhenti setelah lagu ini: aktif" else "Berhenti setelah lagu ini: mati")
    }

    private fun clearTimer() {
        timerJob?.cancel()
        timerActive = false
        timerRemainingMs = 0
    }

    fun cancelTimer() {
        val wasActive = timerActive
        clearTimer()
        if (wasActive) toast("Timer tidur dibatalkan")
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        currentSong = null
        anchor = null
        currentPosition = 0
        duration = 0
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
