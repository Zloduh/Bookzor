package com.bookzor.feature.catalog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

data class RecentBookItem(
    val book: BookEntity,
    val state: PlaybackStateEntity
)

@Composable
fun ContinueListeningShelf(
    recentBooks: List<RecentBookItem>,
    onResumeBook: (BookEntity, PlaybackStateEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (recentBooks.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Continue Listening",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(recentBooks) { item ->
                ContinueListeningCard(
                    item = item,
                    onResume = { onResumeBook(item.book, item.state) }
                )
            }
        }
    }
}

@Composable
fun ContinueListeningCard(
    item: RecentBookItem,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    val book = item.book
    val state = item.state
    val progress = if (book.totalDurationMs > 0) {
        (state.positionMs.toFloat() / book.totalDurationMs).coerceIn(0f, 1f)
    } else 0.15f

    Row(
        modifier = modifier
            .width(280.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C1C1C))
            .clickable { onResume() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2B2B2B)),
            contentAlignment = Alignment.Center
        ) {
            if (!book.coverUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = book.coverUrl,
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                )
            } else {
                Text("🎧", fontSize = 22.sp)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Info & Resume Button
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
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

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = Color(0xFFFF5722),
                trackColor = Color(0xFF333333)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Play circle
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF5722)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Resume",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
