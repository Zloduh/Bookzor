package com.bookzor.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
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

sealed class Screen(val route: String) {
    data object Player : Screen("player")
    data object Catalog : Screen("catalog")
    data object Library : Screen("library")
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
    val allBooks by database.bookDao().observeAllBooks().collectAsState(initial = emptyList())
    val activeInfoHash = playerState.currentInfoHash ?: allBooks.firstOrNull()?.infoHash

    val activeBook by produceState<BookEntity?>(initialValue = null, activeInfoHash) {
        if (activeInfoHash != null) {
            value = withContext(Dispatchers.IO) { database.bookDao().getBook(activeInfoHash) }
        } else {
            value = null
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
                            DrawerDestination.DISCOVER -> navController.navigate(Screen.Catalog.route)
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

                    PlayerScreen(
                        book = activeBook,
                        tracks = tracks,
                        activeTrackIndex = playerState.currentTrackIndex,
                        isPlaying = playerState.isPlaying,
                        isBuffering = playerState.isBuffering,
                        positionMs = playerState.currentPositionMs,
                        chapterDurationMs = playerState.durationMs,
                        playbackSpeed = playerState.playbackSpeed,
                        volumeBoostMb = playerState.volumeBoostMb,
                        pieces = torrentStatus?.pieces,
                        torrentStatus = torrentStatus,
                        bookmarks = bookmarks,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onOpenSleepTimer = { navController.navigate(Screen.SleepTimer.route) },
                        onOpenBooksLibrary = { navController.navigate(Screen.Library.route) },
                        onTogglePlayPause = { playerController.togglePlayPause() },
                        onSeekChapter = { targetMs -> playerController.seekTo(targetMs) },
                        onJumpBack = { seconds -> playerController.jumpBack(seconds) },
                        onJumpForward = { seconds -> playerController.jumpForward(seconds) },
                        onNextTrack = {
                            val nextIdx = playerState.currentTrackIndex + 1
                            if (nextIdx < tracks.size) {
                                val nextUrl = streamServer.getStreamUrl(infoHash, nextIdx)
                                playerController.playStream(nextUrl, infoHash, nextIdx)
                            }
                        },
                        onPrevTrack = {
                            val prevIdx = maxOf(0, playerState.currentTrackIndex - 1)
                            val prevUrl = streamServer.getStreamUrl(infoHash, prevIdx)
                            playerController.playStream(prevUrl, infoHash, prevIdx)
                        },
                        onSelectTrack = { index ->
                            val trackUrl = streamServer.getStreamUrl(infoHash, index)
                            playerController.playStream(trackUrl, infoHash, index)
                        },
                        onSpeedChange = { speed -> playerController.setSpeed(speed) },
                        onBoostChange = { boost -> playerController.setLoudnessBoost(boost) },
                        onBookmark = {
                            if (infoHash.isNotEmpty()) {
                                scope.launch {
                                    database.bookmarkDao().insertBookmark(
                                        com.bookzor.core.database.entity.BookmarkEntity(
                                            infoHash = infoHash,
                                            trackIndex = playerState.currentTrackIndex,
                                            positionMs = playerState.currentPositionMs
                                        )
                                    )
                                }
                            }
                        },
                        onAddBookmarkWithNote = { note ->
                            if (infoHash.isNotEmpty()) {
                                scope.launch {
                                    database.bookmarkDao().insertBookmark(
                                        com.bookzor.core.database.entity.BookmarkEntity(
                                            infoHash = infoHash,
                                            trackIndex = playerState.currentTrackIndex,
                                            positionMs = playerState.currentPositionMs,
                                            note = note
                                        )
                                    )
                                }
                            }
                        },
                        onDeleteBookmark = { bookmark ->
                            scope.launch { database.bookmarkDao().deleteBookmark(bookmark) }
                        }
                    )
                }

                // --- 2. BOOKS / LIBRARY SCREEN ---
                composable(Screen.Library.route) {
                    val libraryBooks by database.bookDao().observeLibraryBooks().collectAsState(initial = emptyList())
                    val libraryItems by produceState<List<LibraryBookItem>>(initialValue = emptyList(), libraryBooks) {
                        while (true) {
                            value = withContext(Dispatchers.IO) {
                                libraryBooks.map { b ->
                                    val s = database.playbackStateDao().getPlaybackState(b.infoHash)
                                    val handle = torrentEngine.getHandle(b.infoHash)
                                    val status = try { if (handle != null && handle.isValid) handle.status() else null } catch (_: Exception) { null }
                                    val progress = status?.progress() ?: 0f
                                    val isFinished = (status?.isFinished ?: false) || progress >= 0.99f
                                    val marks = database.bookmarkDao().getBookmarks(b.infoHash)

                                    LibraryBookItem(
                                        book = b,
                                        state = s,
                                        isFullyDownloaded = isFinished,
                                        downloadProgress = progress,
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
                                val streamUrl = streamServer.getStreamUrl(book.infoHash, 0)
                                playerController.playStream(
                                    streamUrl = streamUrl,
                                    infoHash = book.infoHash,
                                    trackIndex = 0,
                                    title = book.title,
                                    artist = book.author
                                )
                                navController.navigate(Screen.Player.route)
                            }
                        },
                        onPlayBookmark = { book, positionMs ->
                            scope.launch {
                                val streamUrl = streamServer.getStreamUrl(book.infoHash, 0)
                                playerController.playStream(
                                    streamUrl = streamUrl,
                                    infoHash = book.infoHash,
                                    trackIndex = 0,
                                    startPositionMs = positionMs,
                                    title = book.title,
                                    artist = book.author
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
                        onNavigateToDiscover = { navController.navigate(Screen.Catalog.route) },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // --- 3. DISCOVER / SEARCH ON ABB ---
                composable(Screen.Catalog.route) {
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
                                val hash = Regex("btih:([a-fA-F0-9]{40})").find(input)?.groupValues?.get(1)?.lowercase()
                                if (hash != null) {
                                    torrentEngine.downloadMagnet(input)
                                    val streamUrl = streamServer.getStreamUrl(hash, 0)
                                    playerController.playStream(streamUrl, hash, 0)
                                    navController.navigate(Screen.Player.route)
                                }
                            } else if (input.startsWith("http")) {
                                navController.navigate(Screen.Detail.createRoute(input))
                            }
                        }
                    )
                }

                // --- 4. SLEEP TIMER SCREEN ---
                composable(Screen.SleepTimer.route) {
                    SleepTimerScreen(
                        activeRemainingSec = null,
                        onSetTimerMinutes = { mins ->
                            scope.launch {
                                // set timer
                            }
                        },
                        onCancelTimer = {},
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // --- 5. GLOBAL BOOKMARKS SCREEN ---
                composable(Screen.AllBookmarks.route) {
                    val bookmarkGroups by produceState<List<BookBookmarksGroup>>(initialValue = emptyList(), allBooks) {
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
                                    artist = book.author
                                )
                                navController.navigate(Screen.Player.route)
                            }
                        },
                        onDeleteBookmark = { mark ->
                            scope.launch { database.bookmarkDao().deleteBookmark(mark) }
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
                        onStreamClick = { infoHash, magnetUri ->
                            torrentEngine.downloadMagnet(magnetUri)
                            streamServer.start()
                            val streamUrl = streamServer.getStreamUrl(infoHash, fileIndex = 0)
                            playerController.playStream(
                                streamUrl = streamUrl,
                                infoHash = infoHash,
                                trackIndex = 0
                            )
                            navController.navigate(Screen.Player.route)
                        },
                        onPutInLibrary = { infoHash, magnetUri ->
                            scope.launch(Dispatchers.IO) {
                                database.bookDao().setInLibrary(infoHash, true)
                                database.bookDao().setDownloadRequested(infoHash, true)
                                torrentEngine.downloadEntireBook(infoHash, magnetUri)
                            }
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // --- 7. SETTINGS SCREEN ---
                composable(Screen.Settings.route) {
                    val domainManager = remember { com.bookzor.core.scraper.DomainManager() }

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
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }

            // Docked MiniPlayer when on other screens
            if (!isPlayerScreen && activeInfoHash != null && playerState.currentInfoHash != null) {
                MiniPlayerBar(
                    title = activeBook?.title ?: "Audiobook",
                    author = activeBook?.author,
                    coverUrl = activeBook?.coverUrl,
                    isPlaying = playerState.isPlaying,
                    positionMs = playerState.currentPositionMs,
                    durationMs = playerState.durationMs,
                    onTogglePlayPause = { playerController.togglePlayPause() },
                    onJumpBack = { playerController.jumpBack(15) },
                    onJumpForward = { playerController.jumpForward(15) },
                    onExpand = {
                        navController.navigate(Screen.Player.route)
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                )
            }
        }
    }
}
