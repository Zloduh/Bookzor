package com.bookzor.feature.detail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.bookzor.core.scraper.model.BookDetail
import com.bookzor.feature.detail.DetailViewModel
import kotlinx.coroutines.launch

@Composable
fun BookDetailRoute(
    detailUrl: String,
    onPutInLibrary: (String, String, Set<Int>) -> Unit, // infoHash, magnetUri, selectedIndices
    onSearchQuery: (String) -> Unit = {},
    onToggleToRead: (String, Boolean) -> Unit = { _, _ -> },
    onNavigateBack: () -> Unit,
    viewModel: DetailViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { com.bookzor.core.database.BookzorDatabase.getInstance(context) }

    val infoHash = state.bookDetail?.infoHash
    val existingBook by androidx.compose.runtime.produceState<com.bookzor.core.database.entity.BookEntity?>(initialValue = null, infoHash) {
        if (infoHash != null) {
            db.bookDao().observeBook(infoHash).collect { value = it }
        } else {
            value = null
        }
    }
    val isToRead = existingBook?.isToRead ?: false

    LaunchedEffect(detailUrl) {
        viewModel.loadBook(detailUrl)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "Audiobook Details",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                state.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Fetching book details from ABB...", color = Color.Gray)
                    }
                }
                state.errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = state.errorMessage!!, color = Color(0xFFFF6D6D))
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadBook(detailUrl) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Retry")
                        }
                    }
                }
                state.bookDetail != null -> {
                    val detail = state.bookDetail!!
                    BookDetailContent(
                        detail = detail,
                        isToRead = isToRead,
                        onPutInLibrary = { selectedIndices ->
                            onPutInLibrary(detail.infoHash, detail.magnetUri, selectedIndices)
                            scope.launch {
                                snackbarHostState.showSnackbar("Added to Library. Downloading ${selectedIndices.size} files offline.")
                            }
                        },
                        onToggleToRead = {
                            val nextToRead = !isToRead
                            onToggleToRead(detail.infoHash, nextToRead)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (nextToRead) "Added to Listen Later 🔖" else "Removed from Listen Later"
                                )
                            }
                        },
                        onSearchQuery = onSearchQuery
                    )
                }
            }
        }
    }
}

@Composable
fun BookDetailContent(
    detail: BookDetail,
    isToRead: Boolean = false,
    onPutInLibrary: (Set<Int>) -> Unit,
    onToggleToRead: () -> Unit = {},
    onSearchQuery: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isInspectorExpanded by remember { mutableStateOf(false) }
    var selectedFileIndices by remember { mutableStateOf(detail.tracks.indices.toSet()) }

    val selectedBytes = remember(selectedFileIndices) {
        detail.tracks.filter { it.index in selectedFileIndices }.sumOf { it.sizeBytes }
    }
    val selectedSizeText = when {
        selectedBytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", selectedBytes.toFloat() / (1024 * 1024 * 1024))
        selectedBytes >= 1024 * 1024 -> String.format("%d MB", selectedBytes / (1024 * 1024))
        else -> "$selectedBytes bytes"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Cover & Title Row
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!detail.coverUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = detail.coverUrl,
                        contentDescription = detail.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("🎧", fontSize = 32.sp)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Clickable Author
                Text(
                    text = "By ${detail.author}",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { onSearchQuery(detail.author) }
                )
                val narrator = detail.narrator
                if (!narrator.isNullOrBlank()) {
                    Text(
                        text = "Read by $narrator",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { onSearchQuery(narrator) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Author & Narrator Discovery Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSearchQuery(detail.author) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "👤 More by ${detail.author.take(15)}...",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            val narratorName = detail.narrator
            if (!narratorName.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onSearchQuery(narratorName) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "🎙️ Voice: ${narratorName.take(15)}...",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Metadata Pills Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetaPill(label = "Format", value = detail.format)
            MetaPill(label = "Size", value = detail.fileSizeText)
            val duration = detail.durationText
            if (duration != null) {
                MetaPill(label = "Length", value = duration)
            }
            MetaPill(label = "Type", value = if (detail.isAbridged) "Abridged" else "Unabridged")
        }

        Spacer(modifier = Modifier.height(20.dp))

        // THE TWO HERO BUTTONS: [ 📥 Download To Library ] & [ 🔖 Listen Later ]
        Button(
            onClick = { onPutInLibrary(selectedFileIndices) },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Download To Library ($selectedSizeText)",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        androidx.compose.material3.OutlinedButton(
            onClick = onToggleToRead,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (isToRead) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (isToRead) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isToRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Icon(
                imageVector = Icons.Default.Bookmark,
                contentDescription = null,
                tint = if (isToRead) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isToRead) "✓ In Listen Later" else "🔖 Add to Listen Later",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Torrent Inspector Card (with Selective Download Checkboxes!)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isInspectorExpanded = !isInspectorExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Torrent Inspector & Files (${detail.tracks.size})",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Selected ${selectedFileIndices.size}/${detail.tracks.size} files • $selectedSizeText",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Icon(
                    imageVector = if (isInspectorExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.LightGray
                )
            }

            if (isInspectorExpanded) {
                Spacer(modifier = Modifier.height(12.dp))

                // Select All / Deselect All Toggle Buttons for large compilations
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Choose files to download:",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Select All",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { selectedFileIndices = detail.tracks.indices.toSet() }
                                .padding(4.dp)
                        )
                        Text(
                            text = "Deselect All",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clickable { selectedFileIndices = emptySet() }
                                .padding(4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // File list with checkboxes for compilation torrents
                detail.tracks.forEach { track ->
                    val isChecked = track.index in selectedFileIndices
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isChecked) Color(0xFF25201C) else Color.Transparent)
                            .clickable {
                                selectedFileIndices = if (isChecked) {
                                    selectedFileIndices - track.index
                                } else {
                                    selectedFileIndices + track.index
                                }
                            }
                            .padding(vertical = 6.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                selectedFileIndices = if (checked) {
                                    selectedFileIndices + track.index
                                } else {
                                    selectedFileIndices - track.index
                                }
                            },
                            colors = androidx.compose.material3.CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = track.fileName,
                            color = if (isChecked) Color.White else Color.Gray,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = track.sizeString,
                            color = if (isChecked) MaterialTheme.colorScheme.primary else Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Synopsis
        Text(
            text = "Synopsis",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = detail.description.ifEmpty { "No description available for this audiobook." },
            color = Color(0xFFCCCCCC),
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}

@Composable
fun MetaPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text = label, color = Color.Gray, fontSize = 10.sp)
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}
