package com.bookzor.core.torrent

import android.content.Context
import com.bookzor.core.torrent.model.TorrentStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.swig.settings_pack
import org.libtorrent4j.swig.torrent_flags_t
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class TorrentEngine(
    val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val sessionManager = SessionManager()
    private val isRunning = AtomicBoolean(false)
    private val activeHandles = ConcurrentHashMap<String, TorrentHandle>()
    private val schedulers = ConcurrentHashMap<String, PiecePriorityScheduler>()

    val storageDir: File by lazy {
        File(context.filesDir, "audiobooks").apply { if (!exists()) mkdirs() }
    }

    fun start() {
        if (isRunning.getAndSet(true)) return

        val sp = SettingsPack()
        // DHT Bootstrap Routers
        val routers = "router.bittorrent.com:6881,dht.transmissionbt.com:6881,router.utorrent.com:6881,dht.libtorrent.org:25401"
        sp.setString(settings_pack.string_types.dht_bootstrap_nodes.swigValue(), routers)
        sp.setBoolean(settings_pack.bool_types.enable_dht.swigValue(), true)
        sp.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), true)
        sp.setInteger(settings_pack.int_types.connections_limit.swigValue(), 100)
        sp.setInteger(settings_pack.int_types.active_downloads.swigValue(), 10)

        sessionManager.start(SessionParams(sp))
        sessionManager.startDht()

        scope.launch {
            while (isActive) {
                delay(5000)
                try {
                    if (isRunning.get()) {
                        val nodes = sessionManager.dhtNodes()
                        android.util.Log.i("BookzorTorrent", "DHT nodes: $nodes | Active torrents: ${activeHandles.size}")
                    }
                } catch (e: Throwable) {
                    android.util.Log.w("BookzorTorrent", "DHT nodes polling error: ${e.message}")
                }
            }
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        sessionManager.stop()
        activeHandles.clear()
        schedulers.clear()
    }

    fun downloadMagnet(magnetUri: String): TorrentHandle? {
        start()

        val match = Regex("btih:([a-fA-F0-9]{40})").find(magnetUri)
            ?: throw IllegalArgumentException("Invalid magnet URI: missing 40-hex info hash")
        val hashStr = match.groupValues[1].lowercase()

        activeHandles[hashStr]?.let { return it }

        // Start torrent session download with magnet link
        sessionManager.download(magnetUri, storageDir, torrent_flags_t())

        val sha1 = Sha1Hash.parseHex(hashStr)
        val handle = sessionManager.find(sha1)

        if (handle != null && handle.isValid) {
            activeHandles[hashStr] = handle
            // Force immediate announce to trackers and DHT
            try {
                handle.forceReannounce()
                handle.forceDHTAnnounce()
                handle.resume()
            } catch (_: Exception) {}
        }
        return handle
    }

    fun getHandle(infoHash: String): TorrentHandle? {
        val cleanHash = infoHash.trim().lowercase()
        if (cleanHash.length != 40) return null
        return activeHandles[cleanHash] ?: try {
            sessionManager.find(Sha1Hash.parseHex(cleanHash))
        } catch (_: Exception) {
            null
        }
    }

    fun downloadSelectedFiles(infoHash: String, magnetUri: String, selectedIndices: Set<Int>): TorrentHandle? {
        val handle = downloadMagnet(magnetUri) ?: return null

        scope.launch {
            var attempts = 0
            while ((handle.torrentFile() == null || !handle.torrentFile().isValid) && attempts < 80) {
                delay(500)
                attempts++
            }

            val tf = handle.torrentFile()
            if (tf != null && tf.isValid) {
                val numFiles = tf.numFiles()
                for (i in 0 until numFiles) {
                    if (i in selectedIndices) {
                        handle.filePriority(i, org.libtorrent4j.Priority.DEFAULT)
                    } else {
                        handle.filePriority(i, org.libtorrent4j.Priority.IGNORE)
                    }
                }
                handle.resume()
                android.util.Log.i("BookzorTorrent", "Selective download: prioritized ${selectedIndices.size} of $numFiles files for ${tf.name()}")
            }
        }

        return handle
    }

    fun downloadEntireBook(infoHash: String, magnetUri: String): TorrentHandle? {
        val handle = downloadMagnet(magnetUri) ?: return null

        scope.launch {
            var attempts = 0
            while ((handle.torrentFile() == null || !handle.torrentFile().isValid) && attempts < 80) {
                delay(500)
                attempts++
            }

            val tf = handle.torrentFile()
            if (tf != null && tf.isValid) {
                val totalPieces = tf.numPieces()
                for (i in 0 until totalPieces) {
                    handle.piecePriority(i, org.libtorrent4j.Priority.DEFAULT)
                }
                handle.resume()
                android.util.Log.i("BookzorTorrent", "Full book download started: $totalPieces pieces queued for ${tf.name()}")
            }
        }

        return handle
    }

    fun deleteTorrentAndFiles(infoHash: String) {
        val cleanHash = infoHash.lowercase()
        val handle = getHandle(cleanHash)

        if (handle != null && handle.isValid) {
            try {
                val torrentFile = handle.torrentFile()
                val filePath = if (torrentFile != null && torrentFile.isValid) torrentFile.files().filePath(0) else null
                sessionManager.remove(handle, org.libtorrent4j.swig.session_handle.delete_files)

                if (filePath != null) {
                    val file = File(storageDir, filePath)
                    if (file.exists()) file.delete()
                    file.parentFile?.let { parent ->
                        if (parent != storageDir && parent.exists() && parent.listFiles()?.isEmpty() == true) {
                            parent.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("BookzorTorrent", "Error deleting torrent: ${e.message}")
            }
        }

        activeHandles.remove(cleanHash)
        schedulers.remove(cleanHash)
    }

    fun clearAllCache() {
        try {
            val torrents = sessionManager.swig().get_torrents()
            val count = torrents.size
            for (i in 0 until count.toInt()) {
                val th = torrents.get(i)
                sessionManager.swig().remove_torrent(th, org.libtorrent4j.swig.session_handle.delete_files)
            }
        } catch (e: Exception) {
            android.util.Log.w("BookzorTorrent", "Error removing swig torrents during cache wipe: ${e.message}")
        }

        activeHandles.clear()
        schedulers.clear()

        // Delete all files and subdirectories from storage directory
        try {
            storageDir.listFiles()?.forEach { file ->
                file.deleteRecursively()
            }
            // Double check bottom-up cleanup
            storageDir.walkBottomUp().forEach { file ->
                if (file != storageDir) {
                    try { file.delete() } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("BookzorTorrent", "Error deleting storage files: ${e.message}")
        }

        android.util.Log.i("BookzorTorrent", "All torrent cache wiped successfully from disk.")
    }

    fun getScheduler(infoHash: String): PiecePriorityScheduler? {
        val cleanHash = infoHash.lowercase()
        val handle = getHandle(cleanHash) ?: return null
        return schedulers.getOrPut(cleanHash) {
            PiecePriorityScheduler(handle)
        }
    }

    fun observeTorrent(infoHash: String): Flow<TorrentStatus?> = flow {
        val cleanHash = infoHash.trim().lowercase()
        if (cleanHash.length != 40) {
            emit(null)
            return@flow
        }
        while (currentCoroutineContext().isActive) {
            val handle = getHandle(cleanHash)
            if (handle == null || !handle.isValid) {
                emit(null)
            } else {
                val status = try { handle.status() } catch (_: Exception) { null }
                if (status == null) {
                    emit(null)
                    delay(1000)
                    continue
                }

                val info = try {
                    if (handle.torrentFile() != null && handle.torrentFile().isValid) handle.torrentFile() else null
                } catch (_: Exception) {
                    null
                }

                val numPieces = info?.numPieces() ?: 0
                val piecesArray = BooleanArray(numPieces)

                try {
                    val piecesBitfield = status.pieces()
                    if (piecesBitfield != null) {
                        val bitfieldSize = piecesBitfield.size()
                        if (bitfieldSize > 0 && numPieces > 0) {
                            val limit = minOf(numPieces, bitfieldSize)
                            for (i in 0 until limit) {
                                piecesArray[i] = piecesBitfield.getBit(i)
                            }
                        }
                    }
                } catch (_: Throwable) {
                    // Ignore transient native bitfield read errors while swarm connects
                }

                val torrentState = when (status.state()) {
                    org.libtorrent4j.TorrentStatus.State.CHECKING_FILES,
                    org.libtorrent4j.TorrentStatus.State.CHECKING_RESUME_DATA -> TorrentStatus.State.CHECKING
                    org.libtorrent4j.TorrentStatus.State.DOWNLOADING_METADATA -> TorrentStatus.State.DOWNLOADING_METADATA
                    org.libtorrent4j.TorrentStatus.State.DOWNLOADING -> TorrentStatus.State.DOWNLOADING
                    org.libtorrent4j.TorrentStatus.State.FINISHED -> TorrentStatus.State.FINISHED
                    org.libtorrent4j.TorrentStatus.State.SEEDING -> TorrentStatus.State.SEEDING
                    else -> TorrentStatus.State.DOWNLOADING
                }

                emit(
                    TorrentStatus(
                        infoHash = cleanHash,
                        name = status.name().ifEmpty { "Audiobook" },
                        progress = status.progress(),
                        downloadRate = status.downloadRate().toLong(),
                        uploadRate = status.uploadRate().toLong(),
                        seeds = status.numSeeds(),
                        peers = status.numPeers(),
                        state = torrentState,
                        pieceCount = numPieces,
                        pieces = piecesArray
                    )
                )
            }
            delay(1000)
        }
    }.flowOn(Dispatchers.IO)
}

private suspend fun currentCoroutineContext() = kotlin.coroutines.coroutineContext
