package com.example.pemutarmusik

import android.Manifest
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

    val songCountPerFolder = remember(vm.songs) {
        vm.songs.groupBy { it.folderPath }.mapValues { it.value.size }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(hasPermission, sortBy, sortOrder) {
        if (hasPermission) vm.loadSongs(context, sortBy, sortOrder)
    }

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
                                        onShuffle = {
                                            vm.filteredSongs.randomOrNull()
                                                ?.let { vm.playSong(context, it) }
                                            vm.shuffleEnabled = true
                                        }
                                    )
                                    SongList(vm.filteredSongs, vm.currentSong) {
                                        vm.playSong(context, it)
                                    }
                                }
                                1 -> FolderScreen(
                                    vm.allFolders, songCountPerFolder,
                                    { vm.isFolderEnabled(it) }, { vm.toggleFolder(it) }
                                )
                            }
                            1 -> PlaylistScreen(vm.recentlyAdded) { vm.playSong(context, it) }
                        }
                    }
                }
            }
        }
    )

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
                songs = vm.filteredSongs,
                currentSongIndex = vm.currentIndex,
                onSongSelected = { vm.playSong(context, it) },
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