package com.example.pemutarmusik

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import com.example.pemutarmusik.data.MediaSong
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pemutarmusik.data.SortBy
import com.example.pemutarmusik.data.SortOrder
import com.example.pemutarmusik.player.MusicViewModel
import com.example.pemutarmusik.ui.*
import com.example.pemutarmusik.ui.theme.PemutarMusikTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PemutarMusikTheme { MusicApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicApp(vm: MusicViewModel = viewModel()) {
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED
        )
    }

    var sortBy by remember { mutableStateOf(SortBy.TITLE) }
    var sortOrder by remember { mutableStateOf(SortOrder.ASC) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var libraryTab by remember { mutableIntStateOf(0) }
    var showSearch by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    // ── Hapus lagu: konfirmasi -> izin sistem -> hapus file ──
    var pendingDelete by remember { mutableStateOf<MediaSong?>(null) }
    var deleting by remember { mutableStateOf<MediaSong?>(null) }

    fun deleteDirect(song: MediaSong) {
        try {
            if (context.contentResolver.delete(song.uri, null, null) > 0) vm.onSongDeleted(song)
            else vm.toast("Gagal menghapus lagu")
        } catch (e: SecurityException) {
            vm.toast("Tidak punya izin menghapus lagu ini")
        } catch (e: Exception) {
            vm.toast("Gagal menghapus lagu")
        }
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val song = deleting
        deleting = null
        if (song != null) {
            if (result.resultCode == Activity.RESULT_OK) {
                // Android 11+: sistem sudah menghapus file. Android 10: izin baru diberikan, hapus sekarang.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) vm.onSongDeleted(song)
                else deleteDirect(song)
            } else vm.toast("Penghapusan dibatalkan")
        }
    }

    fun requestDelete(song: MediaSong) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pi = MediaStore.createDeleteRequest(context.contentResolver, listOf(song.uri))
                deleting = song
                deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            } else {
                try {
                    if (context.contentResolver.delete(song.uri, null, null) > 0) vm.onSongDeleted(song)
                    else vm.toast("Gagal menghapus lagu")
                } catch (e: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                        deleting = song
                        deleteLauncher.launch(
                            IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                        )
                    } else vm.toast("Tidak punya izin menghapus lagu ini")
                }
            }
        } catch (e: Exception) {
            deleting = null
            vm.toast("Gagal menghapus lagu")
        }
    }

    val menuActions = remember(vm) {
        SongMenuActions(
            isFavorite = { vm.isFavorite(it) },
            isQueued = { vm.isQueued(it) },
            onPlayNext = { vm.queueNext(it) },
            onRemoveNext = { vm.removeFromUpNext(it) },
            onToggleFavorite = { vm.toggleFavorite(it) },
            onDelete = { pendingDelete = it }
        )
    }

    val songCountPerFolder = remember(vm.songs) {
        vm.songs.groupBy { it.folderPath }.mapValues { it.value.size }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(hasPermission) {
        if (hasPermission) vm.loadSongs(context)
    }
    LaunchedEffect(sortBy, sortOrder) { vm.setSort(sortBy, sortOrder) }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // Status untuk FullPlayer
    var showFullPlayer by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Pemutar Musik", fontWeight = FontWeight.Bold) },
                    actions = {
                        if (selectedTab == 0) {
                            IconButton(onClick = {
                                showSearch = !showSearch
                                if (!showSearch) vm.searchQuery = ""
                            }) {
                                Icon(
                                    if (showSearch) Icons.Default.Close else Icons.Default.Search,
                                    "Cari",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        scrolledContainerColor = MaterialTheme.colorScheme.background
                    )
                )

                if (selectedTab == 0) {
                    AnimatedVisibility(visible = showSearch,
                        enter = slideInVertically(), exit = slideOutVertically()) {
                        OutlinedTextField(
                            value = vm.searchQuery,
                            onValueChange = { vm.searchQuery = it },
                            placeholder = { Text("Cari lagu atau artis...") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                                unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                if (selectedTab == 0 && hasPermission) {
                    TabRow(
                        selectedTabIndex = libraryTab,
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Tab(libraryTab == 0, { libraryTab = 0 }) {
                            Text("Lagu", fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
                        }
                        Tab(libraryTab == 1, { libraryTab = 1 }) {
                            Text("Folder", fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column {
                // Mini Player selalu di atas NavigationBar, hanya muncul kalau ada lagu
                AnimatedVisibility(
                    visible = vm.currentSong != null,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    MiniPlayer(
                        song = vm.currentSong,
                        isPlaying = vm.isPlaying,
                        onOpenFullPlayer = { showFullPlayer = true },
                        onPrev = { vm.playPrevious(context) },
                        onPlayPause = { vm.togglePlay() },
                        onNext = { vm.playNext(context) },
                        onClose = { vm.stop() }
                    )
                }
                
                // NavigationBar selalu ada di paling bawah
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Filled.LibraryMusic, null) },
                        label = { Text("Perpustakaan") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null) },
                        label = { Text("Playlist") }
                    )
                }
            }
        },
        content = { innerPadding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (!hasPermission) {
                    PermissionScreen { permissionLauncher.launch(audioPermission()) }
                } else {
                    Box(Modifier.fillMaxSize()) {
                        when (selectedTab) {
                            0 -> when (libraryTab) {
                                0 -> Column(Modifier.fillMaxSize()) {
                                    SortHeader(
                                        total = vm.filteredSongs.size,
                                        sortBy = sortBy,
                                        sortOrder = sortOrder,
                                        onChangeSortBy = { sortBy = it },
                                        onToggleOrder = {
                                            sortOrder = if (sortOrder == SortOrder.ASC)
                                                SortOrder.DESC else SortOrder.ASC
                                        },
                                        onPlayAll = {
                                            vm.filteredSongs.firstOrNull()
                                                ?.let { vm.playSong(context, it) }
                                        },
                                        shuffleActive = vm.shuffleEnabled,
                                        onShuffle = { vm.playShuffled(context) }
                                    )
                                    SongList(vm.filteredSongs, vm.currentSong, menuActions) {
                                        vm.playFromList(context, it)
                                    }
                                }
                                1 -> FolderScreen(
                                    vm.allFolders, songCountPerFolder,
                                    { vm.isFolderEnabled(it) }, { vm.toggleFolder(it) }
                                )
                            }
                            1 -> PlaylistScreen(
                                recentlyAdded = vm.recentlyAdded,
                                history = vm.historySongs,
                                favorites = vm.favoriteSongs,
                                currentSong = vm.currentSong,
                                actions = menuActions,
                                onClearHistory = { vm.clearHistory() },
                                onPlaySong = { vm.playFromList(context, it) }
                            )
                        }
                    }
                }
            }
        }
    )

    // Dialog konfirmasi hapus lagu
    pendingDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Hapus lagu?") },
            text = { Text("\"${song.title}\" akan dihapus permanen dari perangkat.") },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; requestDelete(song) }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Batal") } }
        )
    }

    // Antrean putar (slide dari bawah)
    if (showQueue && vm.currentSong != null) {
        QueueSheet(
            current = vm.currentSong,
            upNext = vm.upNext,
            upcoming = vm.upcoming,
            shuffle = vm.shuffleEnabled,
            onPlay = { vm.playSong(context, it) },
            onRemoveFromUpNext = { vm.removeFromUpNext(it) },
            onClearUpNext = { vm.clearUpNext() },
            onDismiss = { showQueue = false }
        )
    }

    // FullPlayer sebagai ModalBottomSheet
    if (showFullPlayer && vm.currentSong != null) {
        ModalBottomSheet(
            onDismissRequest = { showFullPlayer = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = null,
            modifier = Modifier.fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.background
        ) {
            FullPlayerContent(
                song = vm.currentSong,
                isPlaying = vm.isPlaying,
                currentPosition = vm.currentPosition,
                duration = vm.duration,
                shuffleEnabled = vm.shuffleEnabled,
                repeatMode = vm.repeatMode,
                playbackSpeed = vm.playbackSpeed,
                volume = vm.playerVolume,
                timerActive = vm.timerActive,
                timerRemainingMs = vm.timerRemainingMs,
                stopAfterCurrent = vm.stopAfterCurrent,
                songs = vm.playbackQueue,
                currentSongIndex = vm.currentIndex,
                onSongSelected = { vm.playSong(context, it) },
                menuActions = menuActions,
                onOpenQueue = { showQueue = true },
                onClose = { showFullPlayer = false },
                onPlayPause = { vm.togglePlay() },
                onNext = { vm.playNext(context) },
                onPrev = { vm.playPrevious(context) },
                onSeek = { vm.seekTo(it) },
                onToggleShuffle = { vm.toggleShuffle() },
                onToggleRepeat = { vm.toggleRepeat() },
                onSetSpeed = { vm.setSpeed(it) },
                onSetVolume = { vm.updateVolume(it) },
                onStartTimer = { vm.startTimer(it) },
                onStopAfterCurrent = { vm.setStopAfterCurrent() },
                onCancelTimer = { vm.cancelTimer() }
            )
        }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Butuh izin akses audio", color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRequest) { Text("Izinkan") }
        }
    }
}

private fun audioPermission(): String {
    return if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE
}