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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.bookzor.core.scraper.model.BookDetail
import com.bookzor.feature.detail.DetailViewModel

@Composable
fun BookDetailRoute(
    detailUrl: String,
    onStreamClick: (String, String) -> Unit, // passes infoHash, magnetUri
    onNavigateBack: () -> Unit,
    viewModel: DetailViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(detailUrl) {
        viewModel.loadBook(detailUrl)
    }

    Scaffold(
        containerColor = Color.Black,
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
                        tint = Color.White
                    )
                }
                Text(
                    text = "Audiobook Details",
                    color = Color.White,
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
                        CircularProgressIndicator(color = Color(0xFFFF5722))
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
                        ) {
                            Text("Retry")
                        }
                    }
                }
                state.bookDetail != null -> {
                    BookDetailContent(
                        detail = state.bookDetail!!,
                        onStreamClick = {
                            onStreamClick(state.bookDetail!!.infoHash, state.bookDetail!!.magnetUri)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun BookDetailContent(
    detail: BookDetail,
    onStreamClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isInspectorExpanded by remember { mutableStateOf(false) }

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
                    .background(Color(0xFF222222)),
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
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "By ${detail.author}",
                    color = Color(0xFFFF5722),
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                if (detail.narrator != null) {
                    Text(
                        text = "Read by ${detail.narrator}",
                        color = Color.Gray,
                        fontSize = 13.sp
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

        // HERO CTA: Stream Now
        Button(
            onClick = onStreamClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Stream Now (Sequential Torrent)",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Torrent Inspector Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF161616))
                .clickable { isInspectorExpanded = !isInspectorExpanded }
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Torrent Inspector",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "${detail.tracks.size} audio files • Hash: ${detail.infoHash.take(10)}...",
                        color = Color.Gray,
                        fontSize = 12.sp
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
                Text(
                    text = "Full Info Hash:\n${detail.infoHash}",
                    color = Color(0xFFFFB74D),
                    fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Trackers (${detail.trackers.size}):",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                detail.trackers.take(5).forEach { tracker ->
                    Text(text = "• $tracker", color = Color.Gray, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Audio Files (${detail.tracks.size}):",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                detail.tracks.forEach { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = track.fileName,
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = track.sizeString,
                            color = Color.Gray,
                            fontSize = 12.sp
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
            .background(Color(0xFF1E1E1E))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text = label, color = Color.Gray, fontSize = 10.sp)
        Text(
            text = value,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}
