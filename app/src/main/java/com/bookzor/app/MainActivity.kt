package com.bookzor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.rememberNavController
import com.bookzor.app.navigation.AppNavigation
import com.bookzor.core.database.BookzorDatabase
import com.bookzor.core.player.AudioPlayerController
import com.bookzor.core.torrent.TorrentEngine
import com.bookzor.core.torrent.TorrentStreamServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF5722),
    background = Color(0xFF000000),
    surface = Color(0xFF121212),
    onPrimary = Color(0xFFFFFFFF),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF)
)

class MainActivity : ComponentActivity() {

    private lateinit var torrentEngine: TorrentEngine
    private lateinit var streamServer: TorrentStreamServer
    private lateinit var playerController: AudioPlayerController
    private lateinit var database: BookzorDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Retrieve singleton core engines from Application
        val app = application as BookzorApplication
        database = app.database
        torrentEngine = app.torrentEngine
        streamServer = app.streamServer
        playerController = app.playerController

        checkStoragePermission()

        // Start torrent engine, server, and local audiobook scanner in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                torrentEngine.start()
                streamServer.start()

                // Scan Download/Audiobooks for existing and user-placed books
                val scanner = com.bookzor.app.scanner.LocalAudiobookScanner(
                    context = applicationContext,
                    database = database,
                    storageDir = torrentEngine.storageDir
                )
                scanner.scan()

                // Resume all books marked for offline download in library
                val libraryBooks = database.bookDao().observeAllBooks()
                libraryBooks.collect { books ->
                    for (book in books) {
                        if (book.isDownloadRequested && torrentEngine.getHandle(book.infoHash) == null) {
                            try {
                                val magnet = com.bookzor.core.scraper.MagnetBuilder.build(book.infoHash, book.title)
                                torrentEngine.downloadEntireBook(book.infoHash, magnet)
                                android.util.Log.i("BookzorTorrent", "Resumed offline download for ${book.title}")
                            } catch (e: Exception) {
                                android.util.Log.w("BookzorTorrent", "Failed to resume download for ${book.title}: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("Bookzor", "Background startup error: ${e.message}", e)
            }
        }

        setContent {
            val activeTheme by com.bookzor.core.common.ThemeManager.currentTheme.collectAsState()
            val colorScheme = com.bookzor.app.ui.BookzorTheme.getColorScheme(activeTheme)

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavigation(
                        navController = navController,
                        torrentEngine = torrentEngine,
                        streamServer = streamServer,
                        playerController = playerController,
                        database = database
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun checkStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    try {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        startActivity(intent)
                    } catch (_: Exception) {}
                }
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val permissions = arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            requestPermissions(permissions, 1001)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::torrentEngine.isInitialized && ::database.isInitialized) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    com.bookzor.app.scanner.LocalAudiobookScanner(
                        context = applicationContext,
                        database = database,
                        storageDir = torrentEngine.storageDir
                    ).scan()
                } catch (_: Exception) {}
            }
        }
    }
}
