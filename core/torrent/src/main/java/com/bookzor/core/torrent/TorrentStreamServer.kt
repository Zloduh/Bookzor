package com.bookzor.core.torrent

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class TorrentStreamServer(
    private val torrentEngine: TorrentEngine,
    val port: Int = 8080
) {
    companion object {
        private const val TAG = "BookzorTorrentServer"
    }

    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start() {
        if (isRunning.getAndSet(true)) return

        try {
            serverSocket = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
            Log.i(TAG, "TorrentStreamServer started on 127.0.0.1:$port")

            serverJob = scope.launch {
                while (isActive && isRunning.get()) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        launch {
                            handleClient(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (isRunning.get()) {
                            Log.e(TAG, "Error accepting client connection", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            isRunning.set(false)
            Log.e(TAG, "Failed to start TorrentStreamServer", e)
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        try {
            serverJob?.cancel()
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket", e)
        }
    }

    fun getStreamUrl(infoHash: String, fileIndex: Int): String {
        return "http://127.0.0.1:$port/stream?hash=${infoHash.lowercase()}&fileIndex=$fileIndex"
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        socket.use { s ->
            s.soTimeout = 30_000
            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
            val output = BufferedOutputStream(s.getOutputStream())

            val requestLine = reader.readLine() ?: return@withContext
            val parts = requestLine.split(" ")
            if (parts.size < 2) return@withContext

            val method = parts[0]
            val pathWithQuery = parts[1]

            var rangeHeader: String? = null
            var line: String? = reader.readLine()
            while (!line.isNullOrEmpty()) {
                if (line.startsWith("Range:", ignoreCase = true)) {
                    rangeHeader = line.substringAfter("Range:").trim()
                }
                line = reader.readLine()
            }

            if (!pathWithQuery.startsWith("/stream")) {
                sendNotFound(output)
                return@withContext
            }

            // Parse query params: hash and fileIndex
            val queryString = pathWithQuery.substringAfter("?", "")
            val queryParams = queryString.split("&").associate {
                val kv = it.split("=")
                if (kv.size == 2) kv[0] to kv[1] else "" to ""
            }

            val infoHash = queryParams["hash"]?.lowercase() ?: run {
                sendBadRequest(output, "Missing hash parameter")
                return@withContext
            }
            val fileIndex = queryParams["fileIndex"]?.toIntOrNull() ?: 0

            val handle = torrentEngine.getHandle(infoHash)
            if (handle == null || !handle.isValid) {
                sendNotFound(output, "Torrent handle not ready")
                return@withContext
            }

            val torrentFile = handle.torrentFile()
            if (torrentFile == null || !torrentFile.isValid) {
                sendNotFound(output, "Torrent metadata not yet loaded")
                return@withContext
            }

            val numFiles = torrentFile.numFiles()
            if (fileIndex !in 0 until numFiles) {
                sendBadRequest(output, "Invalid file index $fileIndex (total files: $numFiles)")
                return@withContext
            }

            val totalFileSize = torrentFile.files().fileSize(fileIndex)
            val filePath = torrentFile.files().filePath(fileIndex)
            val targetFile = File(torrentEngine.storageDir, filePath)

            // Notify scheduler of container headers for M4B
            val scheduler = torrentEngine.getScheduler(infoHash)
            if (filePath.endsWith(".m4b", ignoreCase = true)) {
                scheduler?.prioritizeContainerHeaders()
            }

            // Parse byte range
            var startByte = 0L
            var endByte = totalFileSize - 1

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                val rangeSpec = rangeHeader.removePrefix("bytes=").trim()
                val rangeParts = rangeSpec.split("-")
                startByte = rangeParts[0].toLongOrNull() ?: 0L
                endByte = rangeParts.getOrNull(1)?.toLongOrNull() ?: (totalFileSize - 1)
            }

            if (startByte > endByte || startByte >= totalFileSize) {
                sendRangeNotSatisfiable(output, totalFileSize)
                return@withContext
            }

            val contentLength = endByte - startByte + 1

            // Notify scheduler to download pieces around the requested offset
            scheduler?.onSeek(startByte)

            val mimeType = when {
                filePath.endsWith(".m4b", ignoreCase = true) || filePath.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
                filePath.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
                filePath.endsWith(".opus", ignoreCase = true) || filePath.endsWith(".ogg", ignoreCase = true) -> "audio/ogg"
                else -> "application/octet-stream"
            }

            // Write HTTP 206 Partial Content headers
            output.write("HTTP/1.1 206 Partial Content\r\n".toByteArray())
            output.write("Content-Type: $mimeType\r\n".toByteArray())
            output.write("Accept-Ranges: bytes\r\n".toByteArray())
            output.write("Content-Range: bytes $startByte-$endByte/$totalFileSize\r\n".toByteArray())
            output.write("Content-Length: $contentLength\r\n".toByteArray())
            output.write("Connection: close\r\n".toByteArray())
            output.write("\r\n".toByteArray())
            output.flush()

            if (method.equals("HEAD", ignoreCase = true)) {
                return@withContext
            }

            // Stream data from file if present, or wait for pieces
            if (targetFile.exists()) {
                RandomAccessFile(targetFile, "r").use { raf ->
                    raf.seek(startByte)
                    val buffer = ByteArray(64 * 1024)
                    var bytesRemaining = contentLength
                    while (bytesRemaining > 0 && isRunning.get()) {
                        val toRead = minOf(buffer.size.toLong(), bytesRemaining).toInt()
                        val bytesRead = raf.read(buffer, 0, toRead)
                        if (bytesRead == -1) break
                        output.write(buffer, 0, bytesRead)
                        bytesRemaining -= bytesRead
                    }
                    output.flush()
                }
            } else {
                Log.w(TAG, "Target file ${targetFile.name} not created yet on disk by torrent engine")
            }
        }
    }

    private fun sendNotFound(output: BufferedOutputStream, message: String = "Not Found") {
        output.write("HTTP/1.1 404 Not Found\r\nContent-Type: text/plain\r\nConnection: close\r\n\r\n$message".toByteArray())
        output.flush()
    }

    private fun sendBadRequest(output: BufferedOutputStream, message: String = "Bad Request") {
        output.write("HTTP/1.1 400 Bad Request\r\nContent-Type: text/plain\r\nConnection: close\r\n\r\n$message".toByteArray())
        output.flush()
    }

    private fun sendRangeNotSatisfiable(output: BufferedOutputStream, totalSize: Long) {
        output.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$totalSize\r\nConnection: close\r\n\r\n".toByteArray())
        output.flush()
    }
}
