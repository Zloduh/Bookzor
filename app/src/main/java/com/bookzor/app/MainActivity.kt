package com.bookzor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
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

        // Initialize core engines
        database = BookzorDatabase.getInstance(applicationContext)
        torrentEngine = TorrentEngine(applicationContext)
        streamServer = TorrentStreamServer(torrentEngine, initialPort = 8080)

        // Start torrent engine and server in background
        CoroutineScope(Dispatchers.IO).launch {
            torrentEngine.start()
            streamServer.start()
        }

        playerController = AudioPlayerController(
            context = applicationContext,
            playbackStateDao = database.playbackStateDao()
        )

        setContent {
            MaterialTheme(colorScheme = DarkColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
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
        streamServer.stop()
        torrentEngine.stop()
        playerController.release()
        super.onDestroy()
    }
}
