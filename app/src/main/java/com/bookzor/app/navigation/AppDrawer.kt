package com.bookzor.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class DrawerDestination(val title: String, val icon: ImageVector) {
    PLAYER("Player", Icons.Default.PlayArrow),
    BOOKS("Books", Icons.AutoMirrored.Filled.MenuBook),
    TO_READ("To-Read List", Icons.Default.BookmarkAdded),
    DISCOVER("Discover (ABB)", Icons.Default.Search),
    SLEEP_TIMER("Sleep timer", Icons.Default.Timer),
    BOOKMARKS("Bookmarks", Icons.Default.BookmarkBorder),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun AppDrawerContent(
    currentRoute: String,
    onNavigate: (DrawerDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(360.dp)
            .background(Color(0xFF141414))
            .padding(vertical = 24.dp)
    ) {
        // Oleg Brailean Header
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "BOOKZOR",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                letterSpacing = 1.sp
            )
            Text(
                text = "PROFESSIONAL",
                color = Color(0xFFFF5722),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 3.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Divider(color = Color(0xFF282828), thickness = 1.dp)
        Spacer(modifier = Modifier.height(12.dp))

        // Navigation Items
        DrawerDestination.entries.forEach { destination ->
            val isSelected = when (destination) {
                DrawerDestination.PLAYER -> currentRoute.startsWith("player")
                DrawerDestination.BOOKS -> currentRoute == "library"
                DrawerDestination.TO_READ -> currentRoute == "to_read"
                DrawerDestination.DISCOVER -> currentRoute == "catalog"
                DrawerDestination.SLEEP_TIMER -> currentRoute == "sleep_timer"
                DrawerDestination.BOOKMARKS -> currentRoute == "all_bookmarks"
                DrawerDestination.SETTINGS -> currentRoute == "settings"
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(destination) }
                    .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = destination.title,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFD0D0D0),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(18.dp))
                Text(
                    text = destination.title,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 15.sp
                )
            }
        }
    }
}
