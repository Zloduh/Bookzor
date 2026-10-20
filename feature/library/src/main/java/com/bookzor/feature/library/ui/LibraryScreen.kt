package com.bookzor.feature.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bookzor.core.database.entity.BookEntity
import com.bookzor.core.database.entity.PlaybackStateEntity

data class LibraryBookItem(
    val book: BookEntity,
    val state: PlaybackStateEntity?,
    val isFullyDownloaded: Boolean = false,
    val downloadProgress: Float = 0f,
    val bookmarks: List<com.bookzor.core.database.entity.BookmarkEntity> = emptyList()
)

@Composable
fun LibraryScreen(
    books: List<LibraryBookItem>,
    onPlayBook: (BookEntity) -> Unit,
    onPlayBookmark: (BookEntity, Long) -> Unit = { b, _ -> onPlayBook(b) },
    onDownloadBook: (BookEntity) -> Unit,
    onDeleteBook: (BookEntity) -> Unit,
    onNavigateToDiscover: () -> Unit,
    onNavigateBack: () -> Unit = onNavigateToDiscover,
    modifier: Modifier = Modifier
) {
    var bookToDelete by remember { mutableStateOf<BookEntity?>(null) }
    var selectedBookBookmarks by remember { mutableStateOf<Pair<BookEntity, List<com.bookzor.core.database.entity.BookmarkEntity>>?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val availableSpaceGb = remember {
        val freeBytes = context.filesDir.usableSpace
        freeBytes.toFloat() / (1024 * 1024 * 1024)
    }

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFD4D4D4))
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Available space on the device: ${String.format("%.2f", availableSpaceGb)} GB",
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "BOOKS",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onNavigateToDiscover) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Add,
                        contentDescription = "Add Audiobook",
                        tint = Color.White
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (books.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("📚", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Your library is empty",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Search AudioBookBay and tap 'Put in Library' to download books for offline listening.",
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onNavigateToDiscover,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Discover Audiobooks")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(books) { item ->
                        LibraryBookCard(
                            item = item,
                            onPlay = { onPlayBook(item.book) },
                            onDownload = { onDownloadBook(item.book) },
                            onDelete = { bookToDelete = item.book },
                            onOpenBookmarks = {
                                selectedBookBookmarks = item.book to item.bookmarks
                            }
                        )
                    }
                }
            }
        }
    }

    // Bookmarks Picker Dialog for Library book
    if (selectedBookBookmarks != null) {
        val (book, marks) = selectedBookBookmarks!!
        AlertDialog(
            onDismissRequest = { selectedBookBookmarks = null },
            containerColor = Color(0xFF1E1E1E),
            title = {
                Text(
                    text = "Bookmarks for ${book.title}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (marks.isEmpty()) {
                        Text("No bookmarks saved for this book yet.", color = Color.Gray)
                    } else {
                        marks.forEach { mark ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF262626))
                                    .clickable {
                                        onPlayBookmark(book, mark.positionMs)
                                        selectedBookBookmarks = null
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = String.format("%02d:%02d", (mark.positionMs / 1000) / 60, (mark.positionMs / 1000) % 60),
                                    color = Color(0xFFFF5722),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = mark.note ?: "Bookmark",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(onClick = { selectedBookBookmarks = null }) {
                    Text("Close", color = Color.LightGray)
                }
            }
        )
    }

    // Confirmation dialog before deleting book files
    if (bookToDelete != null) {
        val target = bookToDelete!!
        AlertDialog(
            onDismissRequest = { bookToDelete = null },
            containerColor = Color(0xFF1E1E1E),
            title = {
                Text(text = "Remove from Library?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "This will delete the downloaded audiobook files for \"${target.title}\" and free up storage space.",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteBook(target)
                        bookToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Delete Files", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { bookToDelete = null }) {
                    Text("Cancel", color = Color.LightGray)
                }
            }
        )
    }
}

@Composable
fun LibraryBookCard(
    item: LibraryBookItem,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onOpenBookmarks: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val book = item.book
    val state = item.state
    val playedProgress = if (book.totalDurationMs > 0 && state != null) {
        (state.positionMs.toFloat() / book.totalDurationMs).coerceIn(0f, 1f)
    } else 0f

    val sizeText = when {
        book.totalSizeBytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", book.totalSizeBytes.toFloat() / (1024 * 1024 * 1024))
        book.totalSizeBytes >= 1024 * 1024 -> String.format("%d MB", book.totalSizeBytes / (1024 * 1024))
        else -> ""
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF161616))
            .clickable { onPlay() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF262626)),
            contentAlignment = Alignment.Center
        ) {
            if (!book.coverUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = book.coverUrl,
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("🎧", fontSize = 24.sp)
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = book.author,
                color = Color.Gray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Badges row: Format + Offline status
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = book.format,
                    color = Color(0xFFFF5722),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .background(Color(0xFF281C16), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )

                if (sizeText.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = sizeText,
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                }

                if (item.bookmarks.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF2E2218))
                            .clickable { onOpenBookmarks() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "🔖 ${item.bookmarks.size}",
                            color = Color(0xFFFFB74D),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (item.isFullyDownloaded) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "🟢 Offline (100%)",
                        color = Color(0xFF4CAF50),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else if (book.isDownloadRequested) {
                    Spacer(modifier = Modifier.width(6.dp))
                    val percent = (item.downloadProgress * 100).toInt()
                    Text(
                        text = "📥 Downloading $percent%",
                        color = Color(0xFFFFB74D),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Streaming",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }

            // Download Progress Line (if still downloading)
            if (!item.isFullyDownloaded && book.isDownloadRequested) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = item.downloadProgress.coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = Color(0xFFFFB74D),
                    trackColor = Color(0xFF2E2E2E)
                )
            } else if (playedProgress > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = playedProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = Color(0xFFFF5722),
                    trackColor = Color(0xFF2E2E2E)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Action Buttons: Play + Download / Delete
        IconButton(onClick = onPlay) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF5722)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
