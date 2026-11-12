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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
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
import com.bookzor.core.common.UserPreferencesRepository
import com.bookzor.core.scraper.DomainManager
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen(
    domainManager: DomainManager,
    cacheDir: File,
    onClearAllCache: () -> Unit,
    onShareLogs: () -> Unit = {},
    onCheckUpdates: () -> Unit = {},
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTheme by ThemeManager.currentTheme.collectAsState()
    val preferencesRepo = remember { UserPreferencesRepository.getInstance(context) }

    val jumpBackSec by preferencesRepo.jumpBackSec.collectAsState()
    val jumpForwardSec by preferencesRepo.jumpForwardSec.collectAsState()
    val smartRewind by preferencesRepo.smartRewind.collectAsState()
    val wifiOnly by preferencesRepo.wifiOnly.collectAsState()

    val packageVersionName = remember {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                ).versionName ?: "1.0.3"
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.3"
            }
        } catch (_: Exception) {
            "1.0.3"
        }
    }

    var showThemeDialog by remember { mutableStateOf(false) }
    var showDomainDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    var cacheRefreshKey by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    val usedSpaceMb by androidx.compose.runtime.produceState(initialValue = 0L, cacheRefreshKey) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val totalBytes = cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
            totalBytes / (1024 * 1024)
        }
    }

    val freeSpaceGb by androidx.compose.runtime.produceState(initialValue = 0f, cacheRefreshKey) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            context.filesDir.usableSpace.toFloat() / (1024 * 1024 * 1024)
        }
    }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
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
                    subtitle = "Customize -${jumpBackSec}s and +${jumpForwardSec}s buttons",
                    onClick = { showJumpDialog = true }
                )
                Divider(color = Color(0xFF282828))
                SettingsSwitchRow(
                    icon = Icons.Default.Tune,
                    title = "Smart Rewind",
                    subtitle = "Auto-rewind 10-30s after long pause",
                    checked = smartRewind,
                    onCheckedChange = { preferencesRepo.setSmartRewind(it) }
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
                    onCheckedChange = { preferencesRepo.setWifiOnly(it) }
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

            // --- 5. UPDATES & DIAGNOSTICS ---
            SettingsSectionHeader("Updates & Diagnostics")
            SettingsCard {
                SettingsClickableRow(
                    icon = Icons.Default.SystemUpdate,
                    title = "Check for Updates",
                    subtitle = "Search for new releases on GitHub",
                    onClick = onCheckUpdates
                )
                Divider(color = Color(0xFF282828))
                SettingsClickableRow(
                    icon = Icons.Default.Share,
                    title = "Share Diagnostic Logs",
                    subtitle = "Export compressed log archive for debugging",
                    onClick = onShareLogs
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- 6. ABOUT ---
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
                        Text(text = "Version $packageVersionName • Native Android Torrent Streamer", color = Color.Gray, fontSize = 12.sp)
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
            currentBackSec = jumpBackSec,
            currentForwardSec = jumpForwardSec,
            onBackSecChange = { preferencesRepo.setJumpBackSec(it) },
            onForwardSecChange = { preferencesRepo.setJumpForwardSec(it) },
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
                    text = "This will clear $usedSpaceMb MB of downloaded torrent cache pieces from your tablet. Your sideloaded books, reading progress, and bookmarks will not be affected.",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllCache()
                        cacheRefreshKey++
                        showClearCacheDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar("All torrent cache cleared successfully.")
                        }
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
        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
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
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
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
