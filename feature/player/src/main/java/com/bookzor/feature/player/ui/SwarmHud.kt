package com.bookzor.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookzor.core.torrent.model.TorrentStatus

@Composable
fun SwarmHud(
    torrentStatus: TorrentStatus?,
    isBuffering: Boolean,
    modifier: Modifier = Modifier
) {
    if (torrentStatus == null) return

    val seeds = torrentStatus.seeds
    val peers = torrentStatus.peers
    val rate = torrentStatus.downloadRate // bytes/sec

    val statusColor = when {
        seeds >= 5 -> Color(0xFF4CAF50) // Healthy Green
        seeds in 1..4 -> Color(0xFFFFB74D) // Slow Amber
        else -> Color(0xFFFF5252) // Dead/Empty Red
    }

    val speedText = formatSpeed(rate)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E1E1E))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Status indicator dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "$seeds seeds • $peers peers",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )

        if (rate > 0) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "• $speedText",
                color = Color(0xFFFF5722),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        } else if (isBuffering && seeds == 0) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "• Finding peers...",
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
    }
}

fun formatSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec.toFloat() / (1024 * 1024))
        bytesPerSec >= 1024 -> String.format("%d KB/s", bytesPerSec / 1024)
        else -> "$bytesPerSec B/s"
    }
}
