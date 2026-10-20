package com.bookzor.app.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.bookzor.feature.player.ui.MiniPlayerBar
import com.bookzor.feature.player.ui.PlayerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class Screen(val route: String) {
    data object Catalog : Screen("catalog")
    data object Library : Screen("library")
    data object Detail : Screen("detail?url={url}") {
        fun createRoute(url: String): String = "detail?url=" + java.net.URLEncoder.encode(url, "UTF-8")
    }
    data object Player : Screen("player/{infoHash}") {
        fun createRoute(infoHash: String): String = "player/${infoHash.lowercase()}"
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
    val playerState by playerController.playerState.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ""
    val isPlayerScreen = currentRoute.startsWith("player/")

    // Load active book details for MiniPlayer
    val activeInfoHash = playerState.currentInfoHash
    val activeBook by produceState<BookEntity?>(initialValue = null, activeInfoHash) {
        if (activeInfoHash != null) {
            value = withContext(Dispatchers.IO) { database.bookDao().getBook(activeInfoHash) }
        } else {
            value = null
        }
    }

    // Load recent books for Continue Listening Shelf
    val allBooks by database.bookDao().observeAllBooks().collectAsState(initial = emptyList())
    val recentBooks by produceState<List<RecentBookItem>>(initialValue = emptyList(), allBooks) {
        value = withContext(Dispatchers.IO) {
            allBooks.mapNotNull { b ->
                val s = database.playbackStateDao().getPlaybackState(b.infoHash)
                if (s != null) RecentBookItem(b, s) else null
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Catalog.route,
            modifier = Modifier.fillMaxSize()
        ) {
            // --- 1. CATALOG / SEARCH SCREEN ---
            composable(Screen.Catalog.route) {
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
                            navController.navigate(Screen.Player.createRoute(book.infoHash))
                        }
                    },
                    onSubmitDirectMagnet = { input ->
                        if (input.startsWith("magnet:?xt=")) {
                            val hash = Regex("btih:([a-fA-F0-9]{40})").find(input)?.groupValues?.get(1)?.lowercase()
                            if (hash != null) {
                                torrentEngine.downloadMagnet(input)
                                val streamUrl = streamServer.getStreamUrl(hash, 0)
                                playerController.playStream(streamUrl, hash, 0)
                                navController.navigate(Screen.Player.createRoute(hash))
                            }
                        } else if (input.startsWith("http")) {
                            navController.navigate(Screen.Detail.createRoute(input))
                        }
                    }
                )
            }

            // --- 2. BOOK DETAIL & TORRENT INSPECTOR ---
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
                        android.util.Log.i("BookzorNav", "Stream clicked for $infoHash: $magnetUri")
                        torrentEngine.downloadMagnet(magnetUri)
                        streamServer.start()
                        val streamUrl = streamServer.getStreamUrl(infoHash, fileIndex = 0)
                        playerController.playStream(
                            streamUrl = streamUrl,
                            infoHash = infoHash,
                            trackIndex = 0
                        )
                        navController.navigate(Screen.Player.createRoute(infoHash))
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

            // --- 3. OLEG BRAILEAN-INSPIRED PLAYER SCREEN ---
            composable(
                route = Screen.Player.route,
                arguments = listOf(navArgument("infoHash") { type = NavType.StringType })
            ) { backStackEntry ->
                val infoHash = backStackEntry.arguments?.getString("infoHash")?.lowercase() ?: ""

                val bookFlow = database.bookDao().observeBook(infoHash)
                val book by bookFlow.collectAsState(initial = null)

                val tracksFlow = database.audioTrackDao().observeTracks(infoHash)
                val tracks by tracksFlow.collectAsState(initial = emptyList())

                val torrentFlow = torrentEngine.observeTorrent(infoHash)
                val torrentStatus by torrentFlow.collectAsState(initial = null)

                val bookmarksFlow = database.bookmarkDao().observeBookmarks(infoHash)
                val bookmarks by bookmarksFlow.collectAsState(initial = emptyList())

                val displayBook = book ?: BookEntity(
                    infoHash = infoHash,
                    title = torrentStatus?.name ?: "Streaming Audiobook",
                    author = "AudioBookBay",
                    narrator = null,
                    coverUrl = null,
                    description = null,
                    format = "M4B",
                    isAbridged = false,
                    totalSizeBytes = 0L,
                    totalDurationMs = playerState.durationMs,
                    sourceUrl = ""
                )

                PlayerScreen(
                    book = displayBook,
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
                        scope.launch {
                            database.bookmarkDao().insertBookmark(
                                com.bookzor.core.database.entity.BookmarkEntity(
                                    infoHash = infoHash,
                                    trackIndex = playerState.currentTrackIndex,
                                    positionMs = playerState.currentPositionMs
                                )
                            )
                        }
                    },
                    onAddBookmarkWithNote = { note ->
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
                    },
                    onDeleteBookmark = { bookmark ->
                        scope.launch {
                            database.bookmarkDao().deleteBookmark(bookmark)
                        }
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // --- 4. MY LIBRARY & OFFLINE SCREEN ---
            composable(Screen.Library.route) {
                val libraryBooks by database.bookDao().observeLibraryBooks().collectAsState(initial = emptyList())
                val libraryItems by produceState<List<com.bookzor.feature.library.ui.LibraryBookItem>>(initialValue = emptyList(), libraryBooks) {
                    while (true) {
                        value = withContext(Dispatchers.IO) {
                            libraryBooks.map { b ->
                                val s = database.playbackStateDao().getPlaybackState(b.infoHash)
                                val handle = torrentEngine.getHandle(b.infoHash)
                                val status = try { if (handle != null && handle.isValid) handle.status() else null } catch (_: Exception) { null }
                                val progress = status?.progress() ?: 0f
                                val isFinished = (status?.isFinished ?: false) || progress >= 0.99f
                                val marks = database.bookmarkDao().getBookmarks(b.infoHash)

                                com.bookzor.feature.library.ui.LibraryBookItem(
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

                com.bookzor.feature.library.ui.LibraryScreen(
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
                            navController.navigate(Screen.Player.createRoute(book.infoHash))
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
                            navController.navigate(Screen.Player.createRoute(book.infoHash))
                        }
                    },
                    onDownloadBook = { book ->
                        // Request download
                    },
                    onDeleteBook = { book ->
                        scope.launch(Dispatchers.IO) {
                            torrentEngine.deleteTorrentAndFiles(book.infoHash)
                            database.bookDao().setInLibrary(book.infoHash, false)
                            database.bookDao().deleteBook(book.infoHash)
                        }
                    },
                    onNavigateToDiscover = {
                        navController.navigate(Screen.Catalog.route)
                    }
                )
            }
        }

        // --- 4. PERSISTENT DOCKED MINI-PLAYER BAR ---
        if (!isPlayerScreen && activeInfoHash != null) {
            MiniPlayerBar(
                title = activeBook?.title ?: "Audiobook",
                author = activeBook?.author,
                coverUrl = activeBook?.coverUrl,
                isPlaying = playerState.isPlaying,
                positionMs = playerState.currentPositionMs,
                durationMs = playerState.durationMs,
                onTogglePlayPause = { playerController.togglePlayPause() },
                onJumpBack = { playerController.jumpBack(15) },
                onJumpForward = { playerController.jumpForward(30) },
                onExpand = {
                    navController.navigate(Screen.Player.createRoute(activeInfoHash))
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            )
        }
    }
}
