package com.bookzor.feature.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookzor.core.database.entity.AudioTrackEntity

@Composable
fun ChapterListContent(
    tracks: List<AudioTrackEntity>,
    activeTrackIndex: Int,
    currentTrackPositionMs: Long,
    onSelectTrack: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        itemsIndexed(tracks) { index, track ->
            val isPlayingThis = index == activeTrackIndex
            val percentPlayed = when {
                index < activeTrackIndex -> 100
                index == activeTrackIndex && track.durationMs > 0 -> {
                    ((currentTrackPositionMs.toFloat() / track.durationMs) * 100).toInt().coerceIn(0, 100)
                }
                else -> 0
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isPlayingThis) Color(0xFF2E1C16) else Color.Transparent)
                    .clickable { onSelectTrack(index) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = track.fileName.removeSuffix(".mp3").removeSuffix(".m4b"),
                    color = if (isPlayingThis) Color(0xFFFF5722) else Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (track.durationMs > 0) {
                    Text(
                        text = formatDuration(track.durationMs),
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Circular Completion Glyph
                PieProgressIcon(
                    percent = percentPlayed,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterDrawerBottomSheet(
    tracks: List<AudioTrackEntity>,
    activeTrackIndex: Int,
    currentTrackPositionMs: Long,
    onSelectTrack: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF161616),
        modifier = Modifier.fillMaxHeight(0.85f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Chapters & Tracks (${tracks.size})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.LightGray
                    )
                }
            }

            ChapterListContent(
                tracks = tracks,
                activeTrackIndex = activeTrackIndex,
                currentTrackPositionMs = currentTrackPositionMs,
                onSelectTrack = {
                    onSelectTrack(it)
                    onDismiss()
                }
            )
        }
    }
}

@Composable
fun PieProgressIcon(percent: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.5.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        val center = Offset(size.width / 2, size.height / 2)

        // Outer ring
        drawCircle(
            color = Color(0xFFFF5722),
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth)
        )

        // Inner filled arc representing completion
        val sweepAngle = (percent / 100f) * 360f
        drawArc(
            color = Color(0xFFFF5722),
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = true,
            topLeft = Offset(center.x - radius + strokeWidth, center.y - radius + strokeWidth),
            size = Size((radius - strokeWidth) * 2, (radius - strokeWidth) * 2)
        )
    }
}
