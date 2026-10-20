package com.bookzor.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bookzor.core.database.entity.AudioTrackEntity
import com.bookzor.core.database.entity.BookEntity
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    book: BookEntity,
    tracks: List<AudioTrackEntity>,
    activeTrackIndex: Int,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    positionMs: Long,
    chapterDurationMs: Long,
    playbackSpeed: Float,
    volumeBoostMb: Int,
    pieces: BooleanArray?,
    torrentStatus: com.bookzor.core.torrent.model.TorrentStatus? = null,
    bookmarks: List<com.bookzor.core.database.entity.BookmarkEntity> = emptyList(),
    onTogglePlayPause: () -> Unit,
    onSeekChapter: (Long) -> Unit,
    onJumpBack: (Int) -> Unit = {},
    onJumpForward: (Int) -> Unit = {},
    onNextTrack: () -> Unit,
    onPrevTrack: () -> Unit,
    onSelectTrack: (Int) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onBoostChange: (Int) -> Unit,
    onBookmark: () -> Unit,
    onAddBookmarkWithNote: (String?) -> Unit = {},
    onDeleteBookmark: (com.bookzor.core.database.entity.BookmarkEntity) -> Unit = {},
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showSpeedSheet by remember { mutableStateOf(false) }
    var showSleepSheet by remember { mutableStateOf(false) }
    var showChapterSheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }

    var jumpBackSec by remember { androidx.compose.runtime.mutableIntStateOf(15) }
    var jumpForwardSec by remember { androidx.compose.runtime.mutableIntStateOf(30) }

    var sleepRemainingSeconds by remember { mutableStateOf<Long?>(null) }
    var isShakeToReset by remember { mutableStateOf(true) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Black,
        topBar = {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                // Sleep Timer Icon
                IconButton(onClick = { showSleepSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Sleep Timer",
                        tint = if (sleepRemainingSeconds != null) Color(0xFFFF5722) else Color.White
                    )
                }

                // Title
                Text(
                    text = book.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                // Bookmarks List Icon
                IconButton(onClick = { showBookmarksSheet = true }) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Bookmark,
                        contentDescription = "Bookmarks",
                        tint = if (bookmarks.isNotEmpty()) Color(0xFFFF5722) else Color.White
                    )
                }

                // Chapters Drawer Icon
                IconButton(onClick = { showChapterSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = "Chapters",
                        tint = Color.White
                    )
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isTablet = maxWidth >= 600.dp

            if (isTablet) {
                // --- TABLET SPLIT-VIEW CANVAS ---
                Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    // Left Column: Artwork + Player Controls
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PlayerCoverArt(
                            coverUrl = book.coverUrl,
                            title = book.title,
                            onTogglePlayPause = onTogglePlayPause,
                            onNextTrack = onNextTrack,
                            onPrevTrack = onPrevTrack,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(8.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = tracks.getOrNull(activeTrackIndex)?.fileName ?: book.title,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${book.author}${if (book.narrator != null) " • Read by ${book.narrator}" else ""}",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Live BitTorrent Swarm Telemetry HUD
                        SwarmHud(torrentStatus = torrentStatus, isBuffering = isBuffering)

                        Spacer(modifier = Modifier.height(16.dp))

                        if (isBuffering) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFFF5722)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Connecting to swarm & buffering...",
                                    color = Color(0xFFFFB74D),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        DualProgressTracker(
                            bookDurationMs = book.totalDurationMs,
                            bookElapsedMs = positionMs,
                            chapterDurationMs = chapterDurationMs,
                            chapterPositionMs = positionMs,
                            pieces = pieces,
                            onSeekChapter = onSeekChapter
                        )

                        PlayerControlDeck(
                            isPlaying = isPlaying,
                            playbackSpeed = playbackSpeed,
                            volumeBoostMb = volumeBoostMb,
                            jumpBackSec = jumpBackSec,
                            jumpForwardSec = jumpForwardSec,
                            onTogglePlayPause = onTogglePlayPause,
                            onJumpBack = { onJumpBack(jumpBackSec) },
                            onJumpForward = { onJumpForward(jumpForwardSec) },
                            onBookmark = {
                                onBookmark()
                                scope.launch { snackbarHostState.showSnackbar("Bookmark added at ${formatDuration(positionMs)}") }
                            },
                            onOpenSpeedAndBoost = { showSpeedSheet = true },
                            onOpenJumpSettings = { showJumpDialog = true }
                        )
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    // Right Column: Embedded Chapter List & Torrent Swarm for Tablet
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF141414))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Chapters & Tracks (${tracks.size})",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        ChapterListContent(
                            tracks = tracks,
                            activeTrackIndex = activeTrackIndex,
                            currentTrackPositionMs = positionMs,
                            onSelectTrack = onSelectTrack,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                // --- PHONE VERTICAL LAYOUT ---
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PlayerCoverArt(
                        coverUrl = book.coverUrl,
                        title = book.title,
                        onTogglePlayPause = onTogglePlayPause,
                        onNextTrack = onNextTrack,
                        onPrevTrack = onPrevTrack,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(12.dp)
                    )

                    Text(
                        text = tracks.getOrNull(activeTrackIndex)?.fileName ?: book.title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${book.author}${if (book.narrator != null) " • Read by ${book.narrator}" else ""}",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Live BitTorrent Swarm Telemetry HUD
                    SwarmHud(torrentStatus = torrentStatus, isBuffering = isBuffering)

                    Spacer(modifier = Modifier.height(16.dp))

                    DualProgressTracker(
                        bookDurationMs = book.totalDurationMs,
                        bookElapsedMs = positionMs,
                        chapterDurationMs = chapterDurationMs,
                        chapterPositionMs = positionMs,
                        pieces = pieces,
                        onSeekChapter = onSeekChapter
                    )

                    PlayerControlDeck(
                        isPlaying = isPlaying,
                        playbackSpeed = playbackSpeed,
                        volumeBoostMb = volumeBoostMb,
                        jumpBackSec = jumpBackSec,
                        jumpForwardSec = jumpForwardSec,
                        onTogglePlayPause = onTogglePlayPause,
                        onJumpBack = { onJumpBack(jumpBackSec) },
                        onJumpForward = { onJumpForward(jumpForwardSec) },
                        onBookmark = {
                            onBookmark()
                            scope.launch { snackbarHostState.showSnackbar("Bookmark added at ${formatDuration(positionMs)}") }
                        },
                        onOpenSpeedAndBoost = { showSpeedSheet = true },
                        onOpenJumpSettings = { showJumpDialog = true }
                    )
                }
            }
        }
    }

    // Bottom Sheets
    if (showSpeedSheet) {
        SpeedAndBoostBottomSheet(
            currentSpeed = playbackSpeed,
            currentBoostMb = volumeBoostMb,
            onSpeedChange = onSpeedChange,
            onBoostChange = onBoostChange,
            onDismiss = { showSpeedSheet = false }
        )
    }

    if (showSleepSheet) {
        SleepTimerBottomSheet(
            remainingSeconds = sleepRemainingSeconds,
            isShakeToResetEnabled = isShakeToReset,
            onSetTimerMinutes = { mins ->
                sleepRemainingSeconds = mins * 60L
                scope.launch { snackbarHostState.showSnackbar("Sleep timer set for $mins minutes") }
            },
            onCancelTimer = {
                sleepRemainingSeconds = null
                scope.launch { snackbarHostState.showSnackbar("Sleep timer turned off") }
            },
            onToggleShakeToReset = { isShakeToReset = it },
            onDismiss = { showSleepSheet = false }
        )
    }

    if (showChapterSheet) {
        ChapterDrawerBottomSheet(
            tracks = tracks,
            activeTrackIndex = activeTrackIndex,
            currentTrackPositionMs = positionMs,
            onSelectTrack = onSelectTrack,
            onDismiss = { showChapterSheet = false }
        )
    }

    if (showJumpDialog) {
        JumpIntervalsDialog(
            currentBackSec = jumpBackSec,
            currentForwardSec = jumpForwardSec,
            onBackSecChange = { jumpBackSec = it },
            onForwardSecChange = { jumpForwardSec = it },
            onDismiss = { showJumpDialog = false }
        )
    }

    if (showBookmarksSheet) {
        BookmarksBottomSheet(
            bookmarks = bookmarks,
            currentPositionMs = positionMs,
            onSelectBookmark = { b ->
                onSeekChapter(b.positionMs)
                scope.launch { snackbarHostState.showSnackbar("Jumped to bookmark at ${formatDuration(b.positionMs)}") }
            },
            onAddBookmarkWithNote = { note ->
                onAddBookmarkWithNote(note)
                scope.launch { snackbarHostState.showSnackbar("Bookmark added at ${formatDuration(positionMs)}") }
            },
            onDeleteBookmark = onDeleteBookmark,
            onDismiss = { showBookmarksSheet = false }
        )
    }
}

@Composable
fun PlayerCoverArt(
    coverUrl: String?,
    title: String,
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onPrevTrack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E1E))
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onTogglePlayPause() }
                )
            }
            .pointerInput(Unit) {
                var totalDx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDx = 0f },
                    onHorizontalDrag = { _, dragAmount -> totalDx += dragAmount },
                    onDragEnd = {
                        if (totalDx < -100f) {
                            onNextTrack()
                        } else if (totalDx > 100f) {
                            onPrevTrack()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (!coverUrl.isNullOrEmpty()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "🎧\n$title",
                color = Color.LightGray,
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )
        }
    }
}
