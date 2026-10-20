package com.bookzor.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookzor.core.common.AppTheme
import com.bookzor.core.common.ThemeManager
import com.bookzor.core.scraper.DomainManager
import java.io.File

@Composable
fun SettingsScreen(
    domainManager: DomainManager,
    cacheDir: File,
    onClearAllCache: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTheme by ThemeManager.currentTheme.collectAsState()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showDomainDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }

    var wifiOnly by remember { mutableStateOf(false) }
    var smartRewind by remember { mutableStateOf(true) }

    val usedSpaceMb = remember {
        val totalBytes = cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        totalBytes / (1024 * 1024)
    }

    val freeSpaceGb = remember {
        context.filesDir.usableSpace.toFloat() / (1024 * 1024 * 1024)
    }

    Scaffold(
        containerColor = Color.Black,
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
                    text = "SETTINGS",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // --- 1. APPEARANCE & THEME ---
            SettingsSectionHeader("Appearance")
            SettingsCard {
                SettingsClickableRow(
                    icon = Icons.Default.Palette,
                    title = "Color Theme",
                    subtitle = currentTheme.displayName,
                    onClick = { showThemeDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- 2. AUDIO & PLAYBACK ---
            SettingsSectionHeader("Playback")
            SettingsCard {
                SettingsClickableRow(
                    icon = Icons.Default.Tune,
                    title = "Jump Intervals",
                    subtitle = "Customize -15s and +15s buttons",
                    onClick = { showJumpDialog = true }
                )
                Divider(color = Color(0xFF282828))
                SettingsSwitchRow(
                    icon = Icons.Default.Tune,
                    title = "Smart Rewind",
                    subtitle = "Auto-rewind 10-30s after long pause",
                    checked = smartRewind,
                    onCheckedChange = { smartRewind = it }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- 3. AUDIOBOOKBAY & NETWORK ---
            SettingsSectionHeader("Network & ABB Source")
            SettingsCard {
                SettingsClickableRow(
                    icon = Icons.Default.Public,
                    title = "ABB Domain Mirror",
                    subtitle = domainManager.getActiveDomain(),
                    onClick = { showDomainDialog = true }
                )
                Divider(color = Color(0xFF282828))
                SettingsSwitchRow(
                    icon = Icons.Default.Wifi,
                    title = "Download only on Wi-Fi",
                    subtitle = "Prevent cellular data usage for torrents",
                    checked = wifiOnly,
                    onCheckedChange = { wifiOnly = it }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- 4. STORAGE & CACHE ---
            SettingsSectionHeader("Storage")
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(text = "Downloaded Audiobooks", color = Color.White, fontSize = 14.sp)
                            Text(text = "$usedSpaceMb MB cached • ${String.format("%.1f", freeSpaceGb)} GB free", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
                Divider(color = Color(0xFF282828))
                SettingsClickableRow(
                    icon = Icons.Default.Delete,
                    title = "Clear All Torrent Cache",
                    subtitle = "Wipe downloaded piece files",
                    titleColor = Color(0xFFFF5252),
                    onClick = { showClearCacheDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- 5. ABOUT ---
            SettingsSectionHeader("About")
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(text = "Bookzor Professional", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(text = "Version 1.0.0 • Native Android Torrent Streamer", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // Theme Picker Dialog
    if (showThemeDialog) {
        ThemePickerDialog(onDismiss = { showThemeDialog = false })
    }

    // Domain Mirror Picker Dialog
    if (showDomainDialog) {
        DomainPickerDialog(
            domainManager = domainManager,
            onDismiss = { showDomainDialog = false }
        )
    }

    // Jump Intervals Dialog
    if (showJumpDialog) {
        JumpIntervalsDialog(
            currentBackSec = 15,
            currentForwardSec = 15,
            onBackSecChange = {},
            onForwardSecChange = {},
            onDismiss = { showJumpDialog = false }
        )
    }

    // Clear Cache Confirmation Dialog
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            containerColor = Color(0xFF1E1E1E),
            title = {
                Text(text = "Clear All Torrent Cache?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "This will delete all $usedSpaceMb MB of downloaded audio files from your tablet. Your reading progress and bookmarks will be kept.",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllCache()
                        showClearCacheDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Clear Storage", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel", color = Color.LightGray)
                }
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = Color(0xFFFF5722),
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF181818))
    ) {
        content()
    }
}

@Composable
fun SettingsClickableRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    titleColor: Color = Color.White,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(text = title, color = titleColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(text = subtitle, color = Color.Gray, fontSize = 12.sp)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF555555), modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(text = subtitle, color = Color.Gray, fontSize = 12.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFFFF5722),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color(0xFF333333)
            )
        )
    }
}

@Composable
fun DomainPickerDialog(
    domainManager: DomainManager,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        title = {
            Text(text = "Select AudioBookBay Mirror", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DomainManager.DEFAULT_DOMAINS.forEach { domain: String ->
                    val isSelected = domainManager.getActiveDomain() == domain
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF2E1C16) else Color(0xFF121212))
                            .clickable {
                                domainManager.setDomain(domain)
                                onDismiss()
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = domain,
                            color = if (isSelected) Color(0xFFFF5722) else Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", color = Color.LightGray)
            }
        }
    )
}
