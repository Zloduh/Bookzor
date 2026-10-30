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
    initialPort: Int = 8080
) {
    companion object {
        private const val TAG = "BookzorTorrentServer"
    }

    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    var port: Int = initialPort
        private set

    fun start() {
        if (isRunning.getAndSet(true)) return

        try {
            var boundSocket: ServerSocket? = null
            for (p in port..(port + 10)) {
                try {
                    val ss = ServerSocket()
                    ss.reuseAddress = true
                    ss.bind(java.net.InetSocketAddress(InetAddress.getByName("127.0.0.1"), p), 50)
                    boundSocket = ss
                    port = p
                    break
                } catch (_: Exception) {
                    // Try next port
                }
            }

            if (boundSocket == null) {
                // Fallback to any free ephemeral port
                val ss = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
                boundSocket = ss
                port = ss.localPort
            }

            serverSocket = boundSocket
            Log.i(TAG, "TorrentStreamServer successfully bound and listening on 127.0.0.1:$port")

            serverJob = scope.launch {
                while (isActive && isRunning.get()) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        launch(kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
                            Log.d(TAG, "Swallowed client socket exception: ${throwable.message}")
                        }) {
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
        try {
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

                // Check if file is already present on local disk (downloaded previously or placed by user)
                val directFile = torrentEngine.findAudioFile(infoHash, fileIndex)
                if (directFile != null && directFile.exists() && directFile.length() > 0) {
                    serveLocalFile(output, method, directFile, rangeHeader)
                    return@withContext
                }

                // Wait up to 30 seconds for metadata if not ready yet
                var handle = torrentEngine.getHandle(infoHash)
                var attempts = 0
                while ((handle == null || handle.torrentFile() == null || !handle.torrentFile().isValid) && attempts < 60 && isRunning.get()) {
                    kotlinx.coroutines.delay(500)
                    handle = torrentEngine.getHandle(infoHash)
                    attempts++
                }

                if (handle == null || !handle.isValid) {
                    sendNotFound(output, "Torrent handle timeout")
                    return@withContext
                }

                val torrentFile = handle.torrentFile()
                if (torrentFile == null || !torrentFile.isValid) {
                    sendNotFound(output, "Torrent metadata fetch timeout (swarm slow or 0 seeds)")
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

                // Calculate the torrent piece index for startByte
                val pieceLength = torrentFile.pieceLength().toLong()
                val targetPieceIndex = if (pieceLength > 0) {
                    (startByte / pieceLength).toInt().coerceIn(0, torrentFile.numPieces() - 1)
                } else 0

                // Escalate priority for this exact piece
                scheduler?.onSeek(startByte)
                try {
                    handle.setPieceDeadline(targetPieceIndex, 300)
                } catch (_: Exception) {}

                // Wait up to 30 seconds for the target piece to be downloaded from the swarm
                var waitPieceAttempts = 0
                while (!handle.havePiece(targetPieceIndex) && waitPieceAttempts < 60 && isRunning.get()) {
                    kotlinx.coroutines.delay(500)
                    waitPieceAttempts++
                }

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

                // Stream data from file, waiting up to 20 seconds for first bytes if needed
                var waitFileAttempts = 0
                while ((!targetFile.exists() || targetFile.length() <= startByte) && waitFileAttempts < 40 && isRunning.get()) {
                    kotlinx.coroutines.delay(500)
                    waitFileAttempts++
                }

                if (targetFile.exists()) {
                    RandomAccessFile(targetFile, "r").use { raf ->
                        raf.seek(startByte)
                        val buffer = ByteArray(64 * 1024)
                        var bytesRemaining = contentLength
                        while (bytesRemaining > 0 && isRunning.get()) {
                            val toRead = minOf(buffer.size.toLong(), bytesRemaining).toInt()
                            val bytesRead = raf.read(buffer, 0, toRead)
                            if (bytesRead == -1) {
                                // Wait for more bytes if torrent is still downloading
                                kotlinx.coroutines.delay(200)
                                continue
                            }
                            output.write(buffer, 0, bytesRead)
                            bytesRemaining -= bytesRead
                        }
                        output.flush()
                    }
                } else {
                    Log.w(TAG, "Target file ${targetFile.name} timed out waiting for pieces on disk")
                }
            }
        } catch (e: java.io.IOException) {
            // Socket closed, client disconnected, or connection reset - normal in HTTP streaming
            Log.d(TAG, "Client socket disconnected: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Exception handling client connection", e)
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

    private suspend fun serveLocalFile(
        output: BufferedOutputStream,
        method: String,
        targetFile: File,
        rangeHeader: String?
    ) = withContext(Dispatchers.IO) {
        val totalFileSize = targetFile.length()
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
        val mimeType = when {
            targetFile.name.endsWith(".m4b", ignoreCase = true) || targetFile.name.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
            targetFile.name.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
            targetFile.name.endsWith(".opus", ignoreCase = true) || targetFile.name.endsWith(".ogg", ignoreCase = true) -> "audio/ogg"
            targetFile.name.endsWith(".flac", ignoreCase = true) -> "audio/flac"
            else -> "application/octet-stream"
        }

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

        RandomAccessFile(targetFile, "r").use { raf ->
            raf.seek(startByte)
            val buffer = ByteArray(64 * 1024)
            var bytesRemaining = contentLength
            while (bytesRemaining > 0 && isRunning.get()) {
                val bytesToRead = minOf(buffer.size.toLong(), bytesRemaining).toInt()
                val bytesRead = raf.read(buffer, 0, bytesToRead)
                if (bytesRead == -1) break
                output.write(buffer, 0, bytesRead)
                bytesRemaining -= bytesRead
            }
            output.flush()
        }
    }
}
