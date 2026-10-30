package com.bookzor.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.bookzor.core.database.entity.BookmarkEntity
import com.bookzor.core.torrent.model.TorrentStatus
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    book: BookEntity?,
    tracks: List<AudioTrackEntity>,
    activeTrackIndex: Int,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    positionMs: Long,
    chapterDurationMs: Long,
    playbackSpeed: Float,
    volumeBoostMb: Int,
    pieces: BooleanArray?,
    torrentStatus: TorrentStatus? = null,
    bookmarks: List<BookmarkEntity> = emptyList(),
    onOpenDrawer: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenBooksLibrary: () -> Unit,
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
    onDeleteBookmark: (BookmarkEntity) -> Unit = {},
    onSearchQuery: (String) -> Unit = {},
    onOpenBookDetail: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showSpeedSheet by remember { mutableStateOf(false) }
    var showChapterSheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }

    var jumpBackSec by remember { mutableIntStateOf(15) }
    var jumpForwardSec by remember { mutableIntStateOf(15) }

    val hasActiveBook = book != null && book.infoHash.isNotEmpty()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Header Bar matching IMG_4454: [ ☰ ]   [ ⏱ ]   [ 📖 ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Hamburger Menu (Opens Drawer)
                IconButton(onClick = onOpenDrawer) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // 2. Sleep Timer Icon
                IconButton(onClick = onOpenSleepTimer) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Sleep Timer",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // 3. Chapters Drawer Icon
                IconButton(onClick = {
                    if (hasActiveBook) showChapterSheet = true else onOpenBooksLibrary()
                }) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = "Chapters",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
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
            val isTabletLandscape = maxWidth >= 800.dp && maxWidth > maxHeight
            val activeChapterTitle = tracks.getOrNull(activeTrackIndex)?.fileName ?: "01. Chapter title"

            if (isTabletLandscape && hasActiveBook) {
                // Tablet Split-View
                Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Book Metadata Strip
                        OlegBookStrip(
                            book = book,
                            totalDurationMs = book?.totalDurationMs ?: 0L,
                            elapsedMs = positionMs,
                            onOpenLibrary = onOpenBooksLibrary,
                            onSearchQuery = onSearchQuery
                        )

                        // Discovery Chips for Author & Narrator
                        BookAuthorNarratorChips(
                            book = book,
                            onSearchQuery = onSearchQuery,
                            onOpenBookDetail = onOpenBookDetail
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        PlayerCoverArt(
                            coverUrl = book?.coverUrl,
                            title = book?.title ?: "",
                            onTogglePlayPause = onTogglePlayPause,
                            onNextTrack = onNextTrack,
                            onPrevTrack = onPrevTrack,
                            modifier = Modifier.weight(1f).aspectRatio(1f).padding(8.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        SwarmHud(torrentStatus = torrentStatus, isBuffering = isBuffering)

                        Spacer(modifier = Modifier.height(10.dp))

                        OlegPlayerDeck(
                            chapterTitle = activeChapterTitle,
                            positionMs = positionMs,
                            durationMs = chapterDurationMs,
                            pieces = pieces,
                            isPlaying = isPlaying,
                            playbackSpeed = playbackSpeed,
                            jumpBackSec = jumpBackSec,
                            jumpForwardSec = jumpForwardSec,
                            onTogglePlayPause = onTogglePlayPause,
                            onSeek = onSeekChapter,
                            onJumpBack = { onJumpBack(jumpBackSec) },
                            onJumpForward = { onJumpForward(jumpForwardSec) },
                            onNextChapter = onNextTrack,
                            onPrevChapter = onPrevTrack,
                            onBookmark = { showBookmarksSheet = true },
                            onOpenSpeedAndBoost = { showSpeedSheet = true }
                        )
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    // Right Side: Chapter List
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF141414))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Chapters & Tracks (${tracks.size})",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
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
                // Portrait Phone / Tablet Vertical Layout (Exact match to IMG_4454)
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. Top Book Strip
                    OlegBookStrip(
                        book = book,
                        totalDurationMs = book?.totalDurationMs ?: 0L,
                        elapsedMs = positionMs,
                        onOpenLibrary = onOpenBooksLibrary,
                        onSearchQuery = onSearchQuery
                    )

                    // Discovery Chips for Author & Narrator
                    BookAuthorNarratorChips(
                        book = book,
                        onSearchQuery = onSearchQuery,
                        onOpenBookDetail = onOpenBookDetail
                    )

                    // 2. Center Area: Cover Art OR "Please select audiobook"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasActiveBook) {
                            PlayerCoverArt(
                                coverUrl = book?.coverUrl,
                                title = book?.title ?: "",
                                onTogglePlayPause = onTogglePlayPause,
                                onNextTrack = onNextTrack,
                                onPrevTrack = onPrevTrack,
                                modifier = Modifier.fillMaxHeight().aspectRatio(1f)
                            )
                        } else {
                            // Empty State matching IMG_4454
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(110.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1E1E1E))
                                        .clickable { onOpenBooksLibrary() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Select Audiobook",
                                        tint = Color(0xFF666666),
                                        modifier = Modifier.size(56.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Please select audiobook",
                                    color = Color.Gray,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // 3. Swarm Telemetry (if streaming)
                    if (hasActiveBook && torrentStatus != null) {
                        SwarmHud(
                            torrentStatus = torrentStatus,
                            isBuffering = isBuffering,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    // 4. Bottom Control Deck Panel
                    OlegPlayerDeck(
                        chapterTitle = activeChapterTitle,
                        positionMs = positionMs,
                        durationMs = chapterDurationMs,
                        pieces = pieces,
                        isPlaying = isPlaying,
                        playbackSpeed = playbackSpeed,
                        jumpBackSec = jumpBackSec,
                        jumpForwardSec = jumpForwardSec,
                        onTogglePlayPause = {
                            if (hasActiveBook) onTogglePlayPause() else onOpenBooksLibrary()
                        },
                        onSeek = onSeekChapter,
                        onJumpBack = { onJumpBack(jumpBackSec) },
                        onJumpForward = { onJumpForward(jumpForwardSec) },
                        onNextChapter = onNextTrack,
                        onPrevChapter = onPrevTrack,
                        onBookmark = {
                            if (hasActiveBook) showBookmarksSheet = true else onOpenBooksLibrary()
                        },
                        onOpenSpeedAndBoost = { showSpeedSheet = true }
                    )
                }
            }
        }
    }

    // Sheets
    if (showSpeedSheet) {
        SpeedAndBoostBottomSheet(
            currentSpeed = playbackSpeed,
            currentBoostMb = volumeBoostMb,
            onSpeedChange = onSpeedChange,
            onBoostChange = onBoostChange,
            onDismiss = { showSpeedSheet = false }
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

    if (showJumpDialog) {
        JumpIntervalsDialog(
            currentBackSec = jumpBackSec,
            currentForwardSec = jumpForwardSec,
            onBackSecChange = { jumpBackSec = it },
            onForwardSecChange = { jumpForwardSec = it },
            onDismiss = { showJumpDialog = false }
        )
    }
}

@Composable
fun OlegBookStrip(
    book: BookEntity?,
    totalDurationMs: Long,
    elapsedMs: Long,
    onOpenLibrary: () -> Unit,
    onSearchQuery: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val durationText = formatDuration(totalDurationMs)
    val readText = formatDuration(elapsedMs)
    val percent = if (totalDurationMs > 0) ((elapsedMs.toFloat() / totalDurationMs) * 100).toInt().coerceIn(0, 100) else 0

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenLibrary() }
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail or Waveform Box (matching IMG_4454)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center
        ) {
            if (!book?.coverUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = book?.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(text = "〰️", fontSize = 20.sp, color = Color(0xFFFF5722))
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Book title & Duration/Read stats
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book?.title ?: "Book Title",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val author = book?.author
            val narrator = book?.narrator
            if (!author.isNullOrBlank() && author != "Local Library") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "By $author",
                        color = Color(0xFFFF8A65),
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onSearchQuery(author) }
                    )
                    if (!narrator.isNullOrBlank()) {
                        Text(
                            text = " • 🎙️ $narrator",
                            color = Color(0xFF90CAF9),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { onSearchQuery(narrator) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Duration:       $durationText",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Read:           $readText ( $percent% )",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }

        // Cast Icon
        IconButton(onClick = {}) {
            Icon(
                imageVector = Icons.Default.Cast,
                contentDescription = "AirPlay/Cast",
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun BookAuthorNarratorChips(
    book: BookEntity?,
    onSearchQuery: (String) -> Unit,
    onOpenBookDetail: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (book == null) return
    val author = book.author
    val narrator = book.narrator
    val hasAuthor = author.isNotBlank() && author != "Local Library"
    val hasNarrator = !narrator.isNullOrBlank()

    if (hasAuthor || hasNarrator) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasAuthor) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF242424))
                        .clickable { onSearchQuery(author) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "👤 More by ${author.take(18)}${if (author.length > 18) "..." else ""}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (hasNarrator) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF242424))
                        .clickable { onSearchQuery(narrator!!) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "🎙️ Voice: ${narrator!!.take(18)}${if (narrator.length > 18) "..." else ""}",
                        color = Color(0xFFFF5722),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (book.sourceUrl.startsWith("http")) {
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1E1E1E))
                        .clickable { onOpenBookDetail(book.sourceUrl) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "ℹ️ Details",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
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
