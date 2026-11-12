package com.bookzor.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.bookzor.core.common.UserPreferencesRepository
import com.bookzor.core.database.BookzorDatabase
import com.bookzor.core.database.entity.BookEntity
import com.bookzor.core.player.AudioPlayerController
import com.bookzor.core.torrent.TorrentEngine
import com.bookzor.core.torrent.TorrentStreamServer
import com.bookzor.feature.catalog.ui.CatalogRoute
import com.bookzor.feature.catalog.ui.RecentBookItem
import com.bookzor.feature.detail.ui.BookDetailRoute
import com.bookzor.feature.library.ui.AllBookmarksScreen
import com.bookzor.feature.library.ui.BookBookmarksGroup
import com.bookzor.feature.library.ui.LibraryBookItem
import com.bookzor.feature.library.ui.LibraryScreen
import com.bookzor.feature.player.ui.MiniPlayerBar
import com.bookzor.feature.player.ui.PlayerScreen
import com.bookzor.feature.player.ui.SleepTimerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun parseBtih(input: String): String? {
    val hexMatch = Regex("btih:([a-fA-F0-9]{40})", RegexOption.IGNORE_CASE).find(input)
    if (hexMatch != null) return hexMatch.groupValues[1].lowercase()
    val base32Match = Regex("btih:([a-zA-Z2-7]{32})", RegexOption.IGNORE_CASE).find(input)
    if (base32Match != null) {
        val b32 = base32Match.groupValues[1].uppercase()
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var bits = 0
        var bitCount = 0
        val bytes = mutableListOf<Byte>()
        for (char in b32) {
            val valIndex = base32Chars.indexOf(char)
            if (valIndex == -1) continue
            bits = (bits shl 5) or valIndex
            bitCount += 5
            if (bitCount >= 8) {
                bitCount -= 8
                bytes.add(((bits shr bitCount) and 0xFF).toByte())
            }
        }
        return bytes.joinToString("") { "%02x".format(it) }
    }
    return null
}

sealed class Screen(val route: String) {
    data object Player : Screen("player")
    data object Catalog : Screen("catalog?query={query}") {
        fun createRoute(query: String = ""): String = "catalog?query=" + java.net.URLEncoder.encode(query, "UTF-8")
    }
    data object Library : Screen("library")
    data object ToRead : Screen("to_read")
    data object SleepTimer : Screen("sleep_timer")
    data object AllBookmarks : Screen("all_bookmarks")
    data object Settings : Screen("settings")
    data object Detail : Screen("detail?url={url}") {
        fun createRoute(url: String): String = "detail?url=" + java.net.URLEncoder.encode(url, "UTF-8")
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    torrentEngine: TorrentEngine,
    streamServer: TorrentStreamServer,
    playerController: AudioPlayerController,
    database: BookzorDatabase,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val playerState by playerController.playerState.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Player.route
    val isPlayerScreen = currentRoute == Screen.Player.route

    // Active playing or last accessed book
    val latestPlaybackState by database.playbackStateDao().observeLatestPlaybackState().collectAsState(initial = null)
    val mostRecentBook by database.bookDao().observeMostRecentBook().collectAsState(initial = null)
    val allBooks by database.bookDao().observeAllBooks().collectAsState(initial = emptyList())

    val activeInfoHash = playerState.currentInfoHash
        ?: latestPlaybackState?.infoHash
        ?: mostRecentBook?.infoHash
        ?: allBooks.firstOrNull()?.infoHash

    val activeBook by produceState<BookEntity?>(initialValue = null, activeInfoHash) {
        if (activeInfoHash != null) {
            database.bookDao().observeBook(activeInfoHash).collect { value = it }
        } else {
            value = null
        }
    }

    LaunchedEffect(activeInfoHash) {
        if (activeInfoHash != null && playerState.currentInfoHash != activeInfoHash) {
            playerController.loadBookState(activeInfoHash)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            androidx.compose.material3.ModalDrawerSheet(
                drawerContainerColor = Color(0xFF141414)
            ) {
                AppDrawerContent(
                    currentRoute = currentRoute,
                    onNavigate = { destination ->
                        scope.launch { drawerState.close() }
                        when (destination) {
                            DrawerDestination.PLAYER -> navController.navigate(Screen.Player.route) {
                                popUpTo(Screen.Player.route) { inclusive = true }
                            }
                            DrawerDestination.BOOKS -> navController.navigate(Screen.Library.route)
                            DrawerDestination.TO_READ -> navController.navigate(Screen.ToRead.route)
                            DrawerDestination.DISCOVER -> navController.navigate(Screen.Catalog.createRoute())
                            DrawerDestination.SLEEP_TIMER -> navController.navigate(Screen.SleepTimer.route)
                            DrawerDestination.BOOKMARKS -> navController.navigate(Screen.AllBookmarks.route)
                            DrawerDestination.SETTINGS -> navController.navigate(Screen.Settings.route)
                        }
                    }
                )
            }
        }
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Screen.Player.route,
                modifier = Modifier.fillMaxSize()
            ) {
                // --- 1. DEFAULT START SCREEN: PLAYER ---
                composable(Screen.Player.route) {
                    val infoHash = activeInfoHash ?: ""

                    val tracksFlow = database.audioTrackDao().observeTracks(infoHash)
                    val tracks by tracksFlow.collectAsState(initial = emptyList())

                    val torrentFlow = torrentEngine.observeTorrent(infoHash)
                    val torrentStatus by torrentFlow.collectAsState(initial = null)

                    val bookmarksFlow = database.bookmarkDao().observeBookmarks(infoHash)
                    val bookmarks by bookmarksFlow.collectAsState(initial = emptyList())

                    val activeTrackDuration = tracks.getOrNull(playerState.currentTrackIndex)?.durationMs ?: 0L
                    val effectiveChapterDurationMs = when {
                        playerState.durationMs > 0L -> playerState.durationMs
                        activeTrackDuration > 0L -> activeTrackDuration
                        tracks.size <= 1 && (activeBook?.totalDurationMs ?: 0L) > 0L -> activeBook?.totalDurationMs ?: 0L
                        else -> 0L
                    }

                    PlayerScreen(
                        book = activeBook,
                        tracks = tracks,
                        activeTrackIndex = playerState.currentTrackIndex,
                        isPlaying = playerState.isPlaying,
                        isBuffering = playerState.isBuffering,
                        positionMs = playerState.currentPositionMs,
                        chapterDurationMs = effectiveChapterDurationMs,
                        playbackSpeed = playerState.playbackSpeed,
                        volumeBoostMb = playerState.volumeBoostMb,
                        pieces = torrentStatus?.pieces,
                        torrentStatus = torrentStatus,
                        bookmarks = bookmarks,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onOpenSleepTimer = { navController.navigate(Screen.SleepTimer.route) },
                        onOpenBooksLibrary = { navController.navigate(Screen.Library.route) },
                        onTogglePlayPause = {
                            val infoHash = activeInfoHash ?: ""
                            if (!playerController.hasMediaItem && infoHash.isNotEmpty()) {
                                val trackIdx = playerState.currentTrackIndex
                                val posMs = playerState.currentPositionMs
                                val streamUrl = streamServer.getStreamUrl(infoHash, trackIdx)
                                playerController.playStream(
                                    streamUrl = streamUrl,
                                    infoHash = infoHash,
                                    trackIndex = trackIdx,
                                    startPositionMs = posMs,
                                    title = activeBook?.title,
                                    artist = activeBook?.author
                                )
                            } else {
                                playerController.togglePlayPause()
                            }
                        },
                        onSeekChapter = { targetMs -> playerController.seekTo(targetMs) },
                        onJumpBack = { seconds -> playerController.jumpBack(seconds) },
                        onJumpForward = { seconds -> playerController.jumpForward(seconds) },
                        onNextTrack = {
                            val nextIdx = playerState.currentTrackIndex + 1
                            if (nextIdx < tracks.size) {
                                val nextUrl = streamServer.getStreamUrl(infoHash, nextIdx)
                                playerController.playStream(
                                    streamUrl = nextUrl,
                                    infoHash = infoHash,
                                    trackIndex = nextIdx,
                                    startPositionMs = 0L,
                                    title = activeBook?.title,
                                    artist = activeBook?.author,
                                    applySmartRewind = false
                                )
                            }
                        },
                        onPrevTrack = {
                            val prevIdx = maxOf(0, playerState.currentTrackIndex - 1)
                            val prevUrl = streamServer.getStreamUrl(infoHash, prevIdx)
                            playerController.playStream(
                                streamUrl = prevUrl,
                                infoHash = infoHash,
                                trackIndex = prevIdx,
                                startPositionMs = 0L,
                                title = activeBook?.title,
                                artist = activeBook?.author,
                                applySmartRewind = false
                            )
                        },
                        onSelectTrack = { index ->
                            val trackUrl = streamServer.getStreamUrl(infoHash, index)
                            playerController.playStream(
                                streamUrl = trackUrl,
                                infoHash = infoHash,
                                trackIndex = index,
                                startPositionMs = 0L,
                                title = activeBook?.title,
                                artist = activeBook?.author,
                                applySmartRewind = false
                            )
                        },
                        onSpeedChange = { speed -> playerController.setSpeed(speed) },
                        onBoostChange = { boost -> playerController.setLoudnessBoost(boost) },
                        onBookmark = {
                            if (infoHash.isNotEmpty()) {
                                val currentTrack = playerState.currentTrackIndex
                                val currentPos = playerState.currentPositionMs
                                scope.launch(Dispatchers.IO) {
                                    database.bookmarkDao().insertBookmark(
                                        com.bookzor.core.database.entity.BookmarkEntity(
                                            infoHash = infoHash,
                                            trackIndex = currentTrack,
                                            positionMs = currentPos
                                        )
                                    )
                                }
                            }
                        },
                        onSelectBookmark = { bookmark ->
                            if (infoHash.isNotEmpty()) {
                                val targetTrack = bookmark.trackIndex
                                val targetPos = bookmark.positionMs
                                if (!playerController.hasMediaItem ||
                                    playerState.currentInfoHash != infoHash ||
                                    playerState.currentTrackIndex != targetTrack
                                ) {
                                    val streamUrl = streamServer.getStreamUrl(infoHash, targetTrack)
                                    playerController.playStream(
                                        streamUrl = streamUrl,
                                        infoHash = infoHash,
                                        trackIndex = targetTrack,
                                        startPositionMs = targetPos,
                                        title = activeBook?.title,
                                        artist = activeBook?.author,
                                        applySmartRewind = false
                                    )
                                } else {
                                    playerController.seekTo(targetPos)
                                }
                            }
                        },
                        onAddBookmarkWithNote = { targetPosMs, note ->
                            if (infoHash.isNotEmpty()) {
                                val currentTrack = playerState.currentTrackIndex
                                scope.launch(Dispatchers.IO) {
                                    database.bookmarkDao().insertBookmark(
                                        com.bookzor.core.database.entity.BookmarkEntity(
                                            infoHash = infoHash,
                                            trackIndex = currentTrack,
                                            positionMs = targetPosMs,
                                            note = note
                                        )
                                    )
                                }
                            }
                        },
                        onUpdateBookmarkNote = { bookmark, updatedNote ->
                            scope.launch(Dispatchers.IO) {
                                database.bookmarkDao().updateBookmarkNote(bookmark.id, updatedNote)
                            }
                        },
                        onDeleteBookmark = { bookmark ->
                            scope.launch(Dispatchers.IO) { database.bookmarkDao().deleteBookmark(bookmark) }
                        },
                        onSearchQuery = { query ->
                            navController.navigate(Screen.Catalog.createRoute(query))
                        },
                        onOpenBookDetail = { detailUrl ->
                            navController.navigate(Screen.Detail.createRoute(detailUrl))
                        }
                    )
                }

                // --- 2. BOOKS / LIBRARY SCREEN ---
                composable(Screen.Library.route) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    LaunchedEffect(Unit) {
                        withContext(Dispatchers.IO) {
                            try {
                                com.bookzor.app.scanner.LocalAudiobookScanner(
                                    context = context,
                                    database = database,
                                    storageDir = torrentEngine.storageDir
                                ).scan()
                            } catch (_: Exception) {}
                        }
                    }

                    val libraryBooks by database.bookDao().observeLibraryBooks().collectAsState(initial = emptyList())
                    val libraryItems by produceState<List<LibraryBookItem>>(initialValue = emptyList(), libraryBooks) {
                        while (true) {
                            value = withContext(Dispatchers.IO) {
                                libraryBooks.map { b ->
                                    val s = database.playbackStateDao().getPlaybackState(b.infoHash)
                                    val handle = torrentEngine.getHandle(b.infoHash)
                                    val status = try { if (handle != null && handle.isValid) handle.status() else null } catch (_: Exception) { null }
                                    val progress = status?.progress() ?: 0f
                                    val hasLocalFile = torrentEngine.findAudioFile(b.infoHash, 0) != null
                                    val isFinished = (status?.isFinished ?: false) || progress >= 0.99f || hasLocalFile
                                    val marks = database.bookmarkDao().getBookmarks(b.infoHash)

                                    LibraryBookItem(
                                        book = b,
                                        state = s,
                                        isFullyDownloaded = isFinished,
                                        downloadProgress = if (hasLocalFile && progress < 0.99f) 1.0f else progress,
                                        bookmarks = marks
                                    )
                                }
                            }
                            kotlinx.coroutines.delay(1000)
                        }
                    }

                    LibraryScreen(
                        books = libraryItems,
                        onPlayBook = { book ->
                            scope.launch {
                                val savedState = withContext(Dispatchers.IO) {
                                    database.playbackStateDao().getPlaybackState(book.infoHash)
                                }
                                val trackIdx = savedState?.currentTrackIndex ?: 0
                                val posMs = savedState?.positionMs ?: 0L
                                val streamUrl = streamServer.getStreamUrl(book.infoHash, trackIdx)
                                playerController.playStream(
                                    streamUrl = streamUrl,
                                    infoHash = book.infoHash,
                                    trackIndex = trackIdx,
                                    startPositionMs = posMs,
                                    title = book.title,
                                    artist = book.author
                                )
                                navController.navigate(Screen.Player.route)
                            }
                        },
                        onPlayBookmark = { book, bookmark ->
                            scope.launch {
                                val streamUrl = streamServer.getStreamUrl(book.infoHash, bookmark.trackIndex)
                                playerController.playStream(
                                    streamUrl = streamUrl,
                                    infoHash = book.infoHash,
                                    trackIndex = bookmark.trackIndex,
                                    startPositionMs = bookmark.positionMs,
                                    title = book.title,
                                    artist = book.author,
                                    applySmartRewind = false
                                )
                                navController.navigate(Screen.Player.route)
                            }
                        },
                        onDownloadBook = {},
                        onDeleteBook = { book ->
                            scope.launch(Dispatchers.IO) {
                                torrentEngine.deleteTorrentAndFiles(book.infoHash)
                                database.bookDao().setInLibrary(book.infoHash, false)
                                database.bookDao().deleteBook(book.infoHash)
                            }
                        },
                        onSearchQuery = { query ->
                            navController.navigate(Screen.Catalog.createRoute(query))
                        },
                        onOpenBookDetail = { detailUrl ->
                            navController.navigate(Screen.Detail.createRoute(detailUrl))
                        },
                        onNavigateToDiscover = { navController.navigate(Screen.Catalog.route) },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // --- 3. DISCOVER / SEARCH ON ABB ---
                composable(
                    route = "catalog?query={query}",
                    arguments = listOf(navArgument("query") {
                        type = NavType.StringType
                        defaultValue = ""
                    })
                ) { backStackEntry ->
                    val initialQuery = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("query") ?: "", "UTF-8")

                    val recentBooks by produceState<List<RecentBookItem>>(initialValue = emptyList(), allBooks) {
                        value = withContext(Dispatchers.IO) {
                            allBooks.mapNotNull { b ->
                                val s = database.playbackStateDao().getPlaybackState(b.infoHash)
                                if (s != null) RecentBookItem(b, s) else null
                            }
                        }
                    }

                    CatalogRoute(
                        recentBooks = recentBooks,
                        initialQuery = initialQuery,
                        onOpenLibrary = { navController.navigate(Screen.Library.route) },
                        onBookClick = { detailUrl ->
                            navController.navigate(Screen.Detail.createRoute(detailUrl))
                        },
                        onResumeBook = { book, state ->
                            scope.launch {
                                val streamUrl = streamServer.getStreamUrl(book.infoHash, state.currentTrackIndex)
                                playerController.playStream(
                                    streamUrl = streamUrl,
                                    infoHash = book.infoHash,
                                    trackIndex = state.currentTrackIndex,
                                    startPositionMs = state.positionMs,
                                    title = book.title,
                                    artist = book.author
                                )
                                navController.navigate(Screen.Player.route)
                            }
                        },
                        onSubmitDirectMagnet = { input ->
                            if (input.startsWith("magnet:?xt=")) {
                                val hash = parseBtih(input)
                                if (hash != null) {
                                    scope.launch(Dispatchers.IO) {
                                        if (database.bookDao().getBook(hash) == null) {
                                            val dnMatch = Regex("[?&]dn=([^&]+)").find(input)?.groupValues?.get(1)
                                            val title = dnMatch?.let {
                                                try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
                                            } ?: "Direct Magnet (${hash.take(8)})"
                                            database.bookDao().insertOrUpdate(
                                                BookEntity(
                                                    infoHash = hash,
                                                    title = title,
                                                    author = "Unknown Author",
                                                    narrator = null,
                                                    coverUrl = null,
                                                    description = null,
                                                    format = "Audiobook",
                                                    isAbridged = false,
                                                    totalSizeBytes = 0L,
                                                    totalDurationMs = 0L,
                                                    sourceUrl = input,
                                                    isInLibrary = true,
                                                    isDownloadRequested = true,
                                                    lastAccessedAt = System.currentTimeMillis()
                                                )
                                            )
                                        }
                                        withContext(Dispatchers.Main) {
                                            torrentEngine.downloadMagnet(input)
                                            val streamUrl = streamServer.getStreamUrl(hash, 0)
                                            playerController.playStream(streamUrl, hash, 0)
                                            navController.navigate(Screen.Player.route)
                                        }
                                    }
                                }
                            } else if (input.startsWith("http")) {
                                navController.navigate(Screen.Detail.createRoute(input))
                            }
                        }
                    )
                }

                // --- 4. TO-READ LIST SCREEN ---
                composable(Screen.ToRead.route) {
                    val toReadBooks by database.bookDao().observeToReadBooks().collectAsState(initial = emptyList())

                    com.bookzor.feature.library.ui.ToReadScreen(
                        books = toReadBooks,
                        onStreamBook = { book ->
                            scope.launch {
                                val savedState = withContext(Dispatchers.IO) {
                                    database.playbackStateDao().getPlaybackState(book.infoHash)
                                }
                                val trackIdx = savedState?.currentTrackIndex ?: 0
                                val posMs = savedState?.positionMs ?: 0L
                                val streamUrl = streamServer.getStreamUrl(book.infoHash, trackIdx)
                                playerController.playStream(
                                    streamUrl = streamUrl,
                                    infoHash = book.infoHash,
                                    trackIndex = trackIdx,
                                    startPositionMs = posMs,
                                    title = book.title,
                                    artist = book.author
                                )
                                navController.navigate(Screen.Player.route)
                            }
                        },
                        onPutInLibrary = { book ->
                            scope.launch(Dispatchers.IO) {
                                database.bookDao().setInLibrary(book.infoHash, true)
                                database.bookDao().setDownloadRequested(book.infoHash, true)
                                val magnet = com.bookzor.core.scraper.MagnetBuilder.build(book.infoHash, book.title)
                                torrentEngine.downloadEntireBook(book.infoHash, magnet)
                            }
                        },
                        onRemoveFromToRead = { book ->
                            scope.launch(Dispatchers.IO) {
                                database.bookDao().setToRead(book.infoHash, false)
                            }
                        },
                        onNavigateToDiscover = { navController.navigate(Screen.Catalog.createRoute()) },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // --- 4. SLEEP TIMER SCREEN ---
                composable(Screen.SleepTimer.route) {
                    val remainingSec by playerController.sleepTimer.remainingSeconds.collectAsState()
                    val isEndOfChapter by playerController.sleepTimer.isEndOfChapter.collectAsState()

                    SleepTimerScreen(
                        activeRemainingSec = remainingSec,
                        isEndOfChapter = isEndOfChapter,
                        onSetTimerMinutes = { mins ->
                            playerController.sleepTimer.startTimer(mins)
                        },
                        onSetEndOfChapter = {
                            scope.launch {
                                val infoHash = activeInfoHash ?: ""
                                val tracks = withContext(Dispatchers.IO) {
                                    if (infoHash.isNotEmpty()) database.audioTrackDao().getTracks(infoHash) else emptyList()
                                }
                                val activeTrackDuration = tracks.getOrNull(playerState.currentTrackIndex)?.durationMs ?: 0L
                                val effectiveDur = when {
                                    playerState.durationMs > 0L -> playerState.durationMs
                                    activeTrackDuration > 0L -> activeTrackDuration
                                    tracks.size <= 1 && (activeBook?.totalDurationMs ?: 0L) > 0L -> activeBook?.totalDurationMs ?: 0L
                                    else -> 0L
                                }
                                playerController.sleepTimer.startEndOfChapterTimer(effectiveDur, playerState.currentPositionMs)
                            }
                        },
                        onCancelTimer = {
                            playerController.sleepTimer.cancelTimer()
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // --- 5. GLOBAL BOOKMARKS SCREEN ---
                composable(Screen.AllBookmarks.route) {
                    var bookmarkRefreshKey by remember { androidx.compose.runtime.mutableIntStateOf(0) }
                    val bookmarkGroups by produceState<List<BookBookmarksGroup>>(initialValue = emptyList(), allBooks, bookmarkRefreshKey) {
                        value = withContext(Dispatchers.IO) {
                            allBooks.map { b ->
                                val marks = database.bookmarkDao().getBookmarks(b.infoHash)
                                BookBookmarksGroup(b, marks)
                            }
                        }
                    }

                    AllBookmarksScreen(
                        groups = bookmarkGroups,
                        onSelectBookmark = { book, bookmark ->
                            scope.launch {
                                val streamUrl = streamServer.getStreamUrl(book.infoHash, bookmark.trackIndex)
                                playerController.playStream(
                                    streamUrl = streamUrl,
                                    infoHash = book.infoHash,
                                    trackIndex = bookmark.trackIndex,
                                    startPositionMs = bookmark.positionMs,
                                    title = book.title,
                                    artist = book.author,
                                    applySmartRewind = false
                                )
                                navController.navigate(Screen.Player.route)
                            }
                        },
                        onUpdateBookmarkNote = { mark, updatedNote ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    database.bookmarkDao().updateBookmarkNote(mark.id, updatedNote)
                                }
                                bookmarkRefreshKey++
                            }
                        },
                        onDeleteBookmark = { mark ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    database.bookmarkDao().deleteBookmark(mark)
                                }
                                bookmarkRefreshKey++
                            }
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // --- 6. BOOK DETAIL & TORRENT INSPECTOR ---
                composable(
                    route = "detail?url={url}",
                    arguments = listOf(navArgument("url") {
                        type = NavType.StringType
                        defaultValue = ""
                    })
                ) { backStackEntry ->
                    val rawUrl = backStackEntry.arguments?.getString("url") ?: ""
                    val detailUrl = if (rawUrl.isNotEmpty()) java.net.URLDecoder.decode(rawUrl, "UTF-8") else ""

                    BookDetailRoute(
                        detailUrl = detailUrl,
                        onPutInLibrary = { infoHash, magnetUri, selectedIndices ->
                            scope.launch(Dispatchers.IO) {
                                database.bookDao().setInLibrary(infoHash, true)
                                database.bookDao().setDownloadRequested(infoHash, true)
                                torrentEngine.downloadSelectedFiles(infoHash, magnetUri, selectedIndices)
                            }
                        },
                        onSearchQuery = { query ->
                            navController.navigate(Screen.Catalog.createRoute(query))
                        },
                        onToggleToRead = { infoHash, isToRead ->
                            scope.launch(Dispatchers.IO) {
                                database.bookDao().setToRead(infoHash, isToRead)
                            }
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // --- 7. SETTINGS SCREEN ---
                composable(Screen.Settings.route) {
                    val domainManager = remember { com.bookzor.core.scraper.DomainManager() }
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val updateManager = remember { com.bookzor.app.updater.AppUpdateManager(context) }
                    val updateStatus by updateManager.status.collectAsState()

                    com.bookzor.feature.player.ui.SettingsScreen(
                        domainManager = domainManager,
                        cacheDir = torrentEngine.storageDir,
                        onClearAllCache = {
                            scope.launch(Dispatchers.IO) {
                                withContext(Dispatchers.Main) {
                                    playerController.stop()
                                }
                                torrentEngine.clearAllCache()
                                try {
                                    torrentEngine.context.cacheDir.deleteRecursively()
                                } catch (_: Exception) {}
                                database.bookDao().resetAllDownloadFlags()
                            }
                        },
                        onShareLogs = {
                            scope.launch {
                                com.bookzor.app.diagnostics.LogCollector.collectAndShareLogs(context)
                            }
                        },
                        onCheckUpdates = {
                            scope.launch {
                                updateManager.checkForUpdates()
                            }
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )

                    // Update Dialogs
                    when (val status = updateStatus) {
                        is com.bookzor.app.updater.UpdateStatus.Checking -> {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = {},
                                containerColor = Color(0xFF1E1E1E),
                                title = { Text("Checking for Updates...", color = Color.White) },
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    ) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = Color(0xFF00E676),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text("Contacting GitHub Releases...", color = Color.LightGray, fontSize = 14.sp)
                                    }
                                },
                                confirmButton = {}
                            )
                        }
                        is com.bookzor.app.updater.UpdateStatus.Available -> {
                            val info = status.updateInfo
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { updateManager.resetStatus() },
                                containerColor = Color(0xFF1E1E1E),
                                title = {
                                    Text(
                                        text = "Update Available: v${info.versionName}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                text = {
                                    Column {
                                        Text(
                                            text = info.releaseTitle,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(text = info.changelog, color = Color.LightGray, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.height(10.dp))
                                        val mb = info.sizeBytes.toFloat() / (1024 * 1024)
                                        if (mb > 0) {
                                            Text(
                                                text = "Size: ${String.format("%.1f", mb)} MB",
                                                color = Color.Gray,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                },
                                confirmButton = {
                                    androidx.compose.material3.Button(
                                        onClick = { scope.launch { updateManager.downloadAndInstall(info) } },
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF00E676)
                                        )
                                    ) {
                                        Text("Download & Install", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    androidx.compose.material3.OutlinedButton(onClick = { updateManager.resetStatus() }) {
                                        Text("Later", color = Color.LightGray)
                                    }
                                }
                            )
                        }
                        is com.bookzor.app.updater.UpdateStatus.Downloading -> {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = {},
                                containerColor = Color(0xFF1E1E1E),
                                title = { Text("Downloading Update...", color = Color.White, fontWeight = FontWeight.Bold) },
                                text = {
                                    Column {
                                        androidx.compose.material3.LinearProgressIndicator(
                                            progress = status.progress,
                                            modifier = Modifier.fillMaxWidth().height(8.dp),
                                            color = Color(0xFF00E676)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "${String.format("%.1f", status.downloadedMb)} MB / ${String.format("%.1f", status.totalMb)} MB (${(status.progress * 100).toInt()}%)",
                                            color = Color.LightGray,
                                            fontSize = 12.sp
                                        )
                                    }
                                },
                                confirmButton = {}
                            )
                        }
                        is com.bookzor.app.updater.UpdateStatus.UpToDate -> {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { updateManager.resetStatus() },
                                containerColor = Color(0xFF1E1E1E),
                                title = { Text("You're Up to Date!", color = Color.White, fontWeight = FontWeight.Bold) },
                                text = { Text("Bookzor is already running the latest version (v${updateManager.currentVersion}).", color = Color.LightGray) },
                                confirmButton = {
                                    androidx.compose.material3.Button(onClick = { updateManager.resetStatus() }) {
                                        Text("OK")
                                    }
                                }
                            )
                        }
                        is com.bookzor.app.updater.UpdateStatus.Error -> {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { updateManager.resetStatus() },
                                containerColor = Color(0xFF1E1E1E),
                                title = { Text("Update Check", color = Color.White, fontWeight = FontWeight.Bold) },
                                text = { Text(status.message, color = Color.LightGray) },
                                confirmButton = {
                                    androidx.compose.material3.Button(onClick = { updateManager.resetStatus() }) {
                                        Text("OK")
                                    }
                                }
                            )
                        }
                        else -> {}
                    }
                }
            }

            // Docked MiniPlayer when on other screens
            if (!isPlayerScreen && activeBook != null) {
                val navContext = androidx.compose.ui.platform.LocalContext.current
                val navPrefsRepo = remember { com.bookzor.core.common.UserPreferencesRepository.getInstance(navContext) }
                val navJumpBackSec by navPrefsRepo.jumpBackSec.collectAsState()
                val navJumpForwardSec by navPrefsRepo.jumpForwardSec.collectAsState()

                val miniDurationMs = if (playerState.durationMs > 0L) {
                    playerState.durationMs
                } else {
                    activeBook?.totalDurationMs ?: 0L
                }
                MiniPlayerBar(
                    title = activeBook?.title ?: "Audiobook",
                    author = activeBook?.author,
                    coverUrl = activeBook?.coverUrl,
                    isPlaying = playerState.isPlaying,
                    positionMs = playerState.currentPositionMs,
                    durationMs = miniDurationMs,
                    jumpBackSec = navJumpBackSec,
                    jumpForwardSec = navJumpForwardSec,
                    onTogglePlayPause = {
                        val infoHash = activeInfoHash ?: ""
                        if (!playerController.hasMediaItem && infoHash.isNotEmpty()) {
                            val trackIdx = playerState.currentTrackIndex
                            val posMs = playerState.currentPositionMs
                            val streamUrl = streamServer.getStreamUrl(infoHash, trackIdx)
                            playerController.playStream(
                                streamUrl = streamUrl,
                                infoHash = infoHash,
                                trackIndex = trackIdx,
                                startPositionMs = posMs,
                                title = activeBook?.title,
                                artist = activeBook?.author
                            )
                        } else {
                            playerController.togglePlayPause()
                        }
                    },
                    onJumpBack = { playerController.jumpBack(navJumpBackSec) },
                    onJumpForward = { playerController.jumpForward(navJumpForwardSec) },
                    onExpand = {
                        navController.navigate(Screen.Player.route)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                )
            }
        }
    }
}
