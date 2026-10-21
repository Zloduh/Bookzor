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
