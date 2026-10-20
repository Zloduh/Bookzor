package com.bookzor.app.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.bookzor.core.database.BookzorDatabase
import com.bookzor.core.database.entity.BookEntity
import com.bookzor.core.player.AudioPlayerController
import com.bookzor.core.torrent.TorrentEngine
import com.bookzor.core.torrent.TorrentStreamServer
import com.bookzor.feature.catalog.ui.CatalogRoute
import com.bookzor.feature.detail.ui.BookDetailRoute
import com.bookzor.feature.player.ui.PlayerScreen
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    data object Catalog : Screen("catalog")
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

    NavHost(
        navController = navController,
        startDestination = Screen.Catalog.route,
        modifier = modifier
    ) {
        // --- 1. CATALOG / SEARCH SCREEN ---
        composable(Screen.Catalog.route) {
            CatalogRoute(
                onBookClick = { detailUrl ->
                    navController.navigate(Screen.Detail.createRoute(detailUrl))
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
                    // 1. Download magnet & start torrent session
                    torrentEngine.downloadMagnet(magnetUri)

                    // 2. Start local HTTP stream server
                    streamServer.start()

                    // 3. Construct local stream URL for ExoPlayer
                    val streamUrl = streamServer.getStreamUrl(infoHash, fileIndex = 0)

                    // 4. Start ExoPlayer streaming
                    playerController.playStream(
                        streamUrl = streamUrl,
                        infoHash = infoHash,
                        trackIndex = 0
                    )

                    // 5. Navigate to Oleg Brailean-style Player Screen
                    navController.navigate(Screen.Player.createRoute(infoHash))
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

            val playerState by playerController.playerState.collectAsState()
            val bookFlow = database.bookDao().observeBook(infoHash)
            val book by bookFlow.collectAsState(initial = null)

            val tracksFlow = database.audioTrackDao().observeTracks(infoHash)
            val tracks by tracksFlow.collectAsState(initial = emptyList())

            val torrentFlow = torrentEngine.observeTorrent(infoHash)
            val torrentStatus by torrentFlow.collectAsState(initial = null)

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
                onTogglePlayPause = { playerController.togglePlayPause() },
                onSeekChapter = { targetMs -> playerController.seekTo(targetMs) },
                onJumpBack = { playerController.jumpBack(15) },
                onJumpForward = { playerController.jumpForward(30) },
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
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
