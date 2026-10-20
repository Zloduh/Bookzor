package com.bookzor.feature.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun FineScrubbingSlider(
    positionMs: Long,
    durationMs: Long,
    pieces: BooleanArray?,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var scrubRateText by remember { mutableStateOf("Normal Scrubbing") }

    val safeDuration = if (durationMs > 0) durationMs else 1L
    val currentRatio = if (isDragging) dragProgress else (positionMs.toFloat() / safeDuration).coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxWidth()) {
        // Floating Scrubbing Rate Badge
        if (isDragging) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = scrubRateText,
                    color = Color.Black,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .background(Color(0xFFFFB74D), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .pointerInput(safeDuration) {
                    var startDragRatio = 0f
                    var accumulatedDx = 0f

                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            startDragRatio = (offset.x / size.width).coerceIn(0f, 1f)
                            dragProgress = startDragRatio
                            accumulatedDx = 0f
                            scrubRateText = "Normal Scrubbing (100%)"
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dyDp = with(density) { change.position.y.toDp() }

                            // Determine scrubbing rate based on downward drag distance
                            val multiplier = when {
                                dyDp > 120.dp -> {
                                    scrubRateText = "Fine Scrubbing (10%)"
                                    0.10f
                                }
                                dyDp > 80.dp -> {
                                    scrubRateText = "Quarter Speed (25%)"
                                    0.25f
                                }
                                dyDp > 40.dp -> {
                                    scrubRateText = "Half Speed (50%)"
                                    0.50f
                                }
                                else -> {
                                    scrubRateText = "Normal Scrubbing (100%)"
                                    1.0f
                                }
                            }

                            accumulatedDx += dragAmount.x * multiplier
                            val newRatio = (startDragRatio + (accumulatedDx / size.width)).coerceIn(0f, 1f)
                            dragProgress = newRatio
                        },
                        onDragEnd = {
                            isDragging = false
                            val targetMs = (dragProgress * safeDuration).toLong()
                            onSeek(targetMs)
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val trackHeight = 4.dp.toPx()
                val yCenter = canvasHeight / 2

                // 1. Inactive Track Background
                drawRoundRect(
                    color = Color(0xFF2A2A2A),
                    topLeft = Offset(0f, yCenter - trackHeight / 2),
                    size = Size(canvasWidth, trackHeight)
                )

                // 2. Torrent Pieces Cache Micro-bar (drawn directly under the track)
                if (pieces != null && pieces.isNotEmpty()) {
                    val pieceWidth = canvasWidth / pieces.size
                    for (i in pieces.indices) {
                        if (pieces[i]) {
                            drawRect(
                                color = Color(0xFF4CAF50), // Green for cached pieces
                                topLeft = Offset(i * pieceWidth, yCenter + trackHeight / 2 + 1.dp.toPx()),
                                size = Size(pieceWidth, 2.dp.toPx())
                            )
                        }
                    }
                }

                // 3. Active Played Track
                val playedWidth = canvasWidth * currentRatio
                drawRoundRect(
                    color = Color(0xFFFF5722), // Signature Terracotta Accent
                    topLeft = Offset(0f, yCenter - trackHeight / 2),
                    size = Size(playedWidth, trackHeight)
                )

                // 4. Scrubbing Thumb
                val thumbRadius = if (isDragging) 8.dp.toPx() else 6.dp.toPx()
                drawCircle(
                    color = Color.White,
                    radius = thumbRadius,
                    center = Offset(playedWidth.coerceIn(thumbRadius, canvasWidth - thumbRadius), yCenter)
                )
            }
        }

        // Timestamps below slider: Left elapsed, Right remaining
        Row(modifier = Modifier.fillMaxWidth()) {
            val currentMs = if (isDragging) (dragProgress * safeDuration).toLong() else positionMs
            val remainingMs = maxOf(0L, safeDuration - currentMs)

            Text(
                text = formatDuration(currentMs),
                color = Color.LightGray,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "-${formatDuration(remainingMs)}",
                color = Color.LightGray,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
