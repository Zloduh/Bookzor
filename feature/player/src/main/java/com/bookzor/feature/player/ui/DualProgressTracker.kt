package com.bookzor.feature.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DualProgressTracker(
    bookDurationMs: Long,
    bookElapsedMs: Long,
    chapterDurationMs: Long,
    chapterPositionMs: Long,
    pieces: BooleanArray?,
    onSeekChapter: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // --- 1. OVERALL BOOK PROGRESS STRIP (Oleg Brailean Signature) ---
        val safeBookDuration = if (bookDurationMs > 0) bookDurationMs else 1L
        val bookPercent = ((bookElapsedMs.toFloat() / safeBookDuration) * 100).toInt().coerceIn(0, 100)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Duration: ${formatDuration(bookDurationMs)}",
                    color = Color(0xFFD0D0D0),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Played: ${formatDuration(bookElapsedMs)} ( $bookPercent% )",
                    color = Color(0xFFFF5722),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                // Circular Progress Pie Icon [◑]
                PieProgressIcon(
                    percent = bookPercent,
                    modifier = Modifier.size(14.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Thin Overall Book Progress Bar
            Canvas(modifier = Modifier.fillMaxWidth().height(3.dp)) {
                val ratio = (bookElapsedMs.toFloat() / safeBookDuration).coerceIn(0f, 1f)
                drawRoundRect(
                    color = Color(0xFF333333),
                    size = Size(size.width, size.height)
                )
                drawRoundRect(
                    color = Color(0xFFFF5722),
                    size = Size(size.width * ratio, size.height)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 2. CURRENT CHAPTER / FILE TRACKER & BUFFER ---
        FineScrubbingSlider(
            positionMs = chapterPositionMs,
            durationMs = chapterDurationMs,
            pieces = pieces,
            onSeek = onSeekChapter
        )
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
