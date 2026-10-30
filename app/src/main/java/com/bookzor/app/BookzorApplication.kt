package com.bookzor.app

import android.app.Application
import com.bookzor.core.database.BookzorDatabase
import com.bookzor.core.player.AudioPlayerController
import com.bookzor.core.torrent.TorrentEngine
import com.bookzor.core.torrent.TorrentStreamServer

class BookzorApplication : Application() {

    lateinit var database: BookzorDatabase
        private set
    lateinit var torrentEngine: TorrentEngine
        private set
    lateinit var streamServer: TorrentStreamServer
        private set
    lateinit var playerController: AudioPlayerController
        private set

    override fun onCreate() {
        super.onCreate()

        // Set up global crash logger
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                android.util.Log.e("BookzorCrash", "FATAL UNCAUGHT EXCEPTION on thread ${thread.name}", throwable)
                val crashFile = java.io.File(filesDir, "latest_crash.txt")
                val sw = java.io.StringWriter()
                val pw = java.io.PrintWriter(sw)
                throwable.printStackTrace(pw)
                crashFile.writeText("Thread: ${thread.name}\nTime: ${java.util.Date()}\n\n$sw")
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        database = BookzorDatabase.getInstance(this)
        torrentEngine = TorrentEngine(this)
        streamServer = TorrentStreamServer(torrentEngine, initialPort = 8080)
        playerController = AudioPlayerController(
            context = this,
            playbackStateDao = database.playbackStateDao(),
            bookDao = database.bookDao()
        )
    }
}
