package com.bookzor.app.scanner

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import com.bookzor.core.database.BookzorDatabase
import com.bookzor.core.database.entity.AudioTrackEntity
import com.bookzor.core.database.entity.BookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class LocalAudiobookScanner(
    private val context: Context,
    private val database: BookzorDatabase,
    private val storageDir: File
) {
    companion object {
        private const val TAG = "BookzorScanner"
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4b", "m4a", "opus", "ogg", "flac", "aac", "wav")
    }

    suspend fun scan() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting scan() in ${storageDir.absolutePath}")
        if (!storageDir.exists() || !storageDir.isDirectory) {
            Log.d(TAG, "Storage directory not ready: ${storageDir.absolutePath}")
            return@withContext
        }

        // 1. First deduplicate any existing books sharing the same audio files or titles
        deduplicateBooks()

        val entries = storageDir.listFiles() ?: return@withContext
        Log.i(TAG, "Scanning ${entries.size} entries in ${storageDir.absolutePath} for audiobooks...")

        for (entry in entries) {
            try {
                if (entry.isDirectory && !entry.name.startsWith(".")) {
                    scanBookDirectory(entry)
                } else if (entry.isFile && isAudioFile(entry)) {
                    scanSingleAudioFile(entry)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error scanning ${entry.name}: ${e.message}")
            }
        }

        // 2. Run deduplication once more after scan to eliminate any generated ghost copies
        deduplicateBooks()
    }

    private suspend fun scanBookDirectory(dir: File) {
        val audioFiles = dir.walkTopDown()
            .filter { it.isFile && isAudioFile(it) }
            .sortedBy { it.name.lowercase() }
            .toList()

        if (audioFiles.isEmpty()) return

        // Check if an existing book in the database matches this directory or any of its files
        val existingBook = findExistingBookForDirectory(dir, audioFiles)

        if (existingBook != null) {
            Log.i(TAG, "Directory '${dir.name}' matches existing book '${existingBook.title}' (${existingBook.infoHash})")
            database.bookDao().setInLibrary(existingBook.infoHash, true)
            database.bookDao().setDownloadRequested(existingBook.infoHash, false)

            // Update track paths and durations so streamServer and PlayerScreen have accurate metadata
            val existingTracks = database.audioTrackDao().getTracks(existingBook.infoHash)
            val needsDurationScan = existingBook.totalDurationMs <= 0L ||
                existingTracks.isEmpty() ||
                existingTracks.any { it.durationMs <= 0L }

            if (needsDurationScan) {
                val mmr = MediaMetadataRetriever()
                var totalDuration = 0L
                val trackEntities = audioFiles.mapIndexed { idx, file ->
                    val existingTrack = existingTracks.getOrNull(idx)
                    var d = existingTrack?.durationMs ?: 0L
                    if (d <= 0L) {
                        try {
                            mmr.setDataSource(file.absolutePath)
                            d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        } catch (_: Exception) {}
                    }
                    totalDuration += d
                    AudioTrackEntity(
                        id = "${existingBook.infoHash}_$idx",
                        infoHash = existingBook.infoHash,
                        trackIndex = idx,
                        fileName = file.relativeTo(storageDir).path,
                        fileSizeBytes = file.length(),
                        durationMs = d
                    )
                }
                try { mmr.release() } catch (_: Exception) {}
                database.audioTrackDao().insertTracks(trackEntities)
                if (totalDuration > 0L) {
                    database.bookDao().updateTotalDuration(existingBook.infoHash, totalDuration)
                }
            }
            return
        }

        // Brand-new manual book not previously in DB
        val infoHash = generateHash("dir_${dir.name.lowercase()}")
        val existing = database.bookDao().getBook(infoHash)

        var author = "Local Library"
        var coverPath: String? = null
        var totalDuration = 0L

        val localCover = dir.listFiles()?.firstOrNull {
            it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp")
        }
        if (localCover != null) {
            coverPath = localCover.absolutePath
        }

        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(audioFiles.first().absolutePath)
            author = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?: "Local Library"

            if (coverPath == null) {
                val pic = mmr.embeddedPicture
                if (pic != null) {
                    val coverFile = File(dir, ".cover.jpg")
                    FileOutputStream(coverFile).use { it.write(pic) }
                    coverPath = coverFile.absolutePath
                }
            }
        } catch (_: Exception) {}

        val trackDurations = LongArray(audioFiles.size)
        for (i in audioFiles.indices) {
            try {
                mmr.setDataSource(audioFiles[i].absolutePath)
                val d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                trackDurations[i] = d
                totalDuration += d
            } catch (_: Exception) {}
        }
        try { mmr.release() } catch (_: Exception) {}

        val totalSize = audioFiles.sumOf { it.length() }
        val format = audioFiles.first().extension.uppercase()

        val book = BookEntity(
            infoHash = infoHash,
            title = existing?.title ?: dir.name,
            author = author,
            narrator = existing?.narrator,
            coverUrl = coverPath ?: existing?.coverUrl,
            description = existing?.description ?: "Local audiobook in Download/Audiobooks/${dir.name}",
            format = format,
            isAbridged = false,
            totalSizeBytes = totalSize,
            totalDurationMs = totalDuration,
            sourceUrl = dir.absolutePath,
            isInLibrary = existing?.isInLibrary ?: true,
            isDownloadRequested = existing?.isDownloadRequested ?: false,
            isToRead = existing?.isToRead ?: false,
            lastAccessedAt = existing?.lastAccessedAt ?: System.currentTimeMillis()
        )
        database.bookDao().insertOrUpdate(book)

        val trackEntities = audioFiles.mapIndexed { idx, file ->
            AudioTrackEntity(
                id = "${infoHash}_$idx",
                infoHash = infoHash,
                trackIndex = idx,
                fileName = file.relativeTo(storageDir).path,
                fileSizeBytes = file.length(),
                durationMs = trackDurations[idx]
            )
        }
        database.audioTrackDao().insertTracks(trackEntities)
        Log.i(TAG, "Indexed manual audiobook '${book.title}' with ${trackEntities.size} tracks (${totalSize / (1024 * 1024)} MB)")
    }

    private suspend fun scanSingleAudioFile(file: File) {
        // Check if an existing book in the database already claims this file
        val existingBook = findExistingBookForFile(file)

        if (existingBook != null) {
            Log.i(TAG, "File '${file.name}' matches existing book '${existingBook.title}' (${existingBook.infoHash})")
            database.bookDao().setInLibrary(existingBook.infoHash, true)
            database.bookDao().setDownloadRequested(existingBook.infoHash, false)

            // Ensure track fileName points to this file and duration is extracted if missing
            val existingTracks = database.audioTrackDao().getTracks(existingBook.infoHash)
            val existingDuration = existingTracks.firstOrNull()?.durationMs ?: 0L
            if (existingTracks.isEmpty() || existingDuration <= 0L || existingBook.totalDurationMs <= 0L) {
                var duration = maxOf(existingDuration, existingBook.totalDurationMs)
                if (duration <= 0L) {
                    val mmr = MediaMetadataRetriever()
                    try {
                        mmr.setDataSource(file.absolutePath)
                        duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    } catch (_: Exception) {}
                    try { mmr.release() } catch (_: Exception) {}
                }
                val track = AudioTrackEntity(
                    id = "${existingBook.infoHash}_0",
                    infoHash = existingBook.infoHash,
                    trackIndex = 0,
                    fileName = file.name,
                    fileSizeBytes = file.length(),
                    durationMs = duration
                )
                database.audioTrackDao().insertTracks(listOf(track))
                if (duration > 0L) {
                    database.bookDao().updateTotalDuration(existingBook.infoHash, duration)
                }
            }
            return
        }

        // Brand-new manual single-file book not in DB
        val title = file.nameWithoutExtension
        val infoHash = generateHash("file_${file.name.lowercase()}")
        val existing = database.bookDao().getBook(infoHash)

        var author = "Local Library"
        var duration = 0L
        var coverPath: String? = null

        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(file.absolutePath)
            author = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?: "Local Library"
            duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

            val pic = mmr.embeddedPicture
            if (pic != null) {
                val coverFile = File(storageDir, ".${file.nameWithoutExtension}_cover.jpg")
                FileOutputStream(coverFile).use { it.write(pic) }
                coverPath = coverFile.absolutePath
            }
        } catch (_: Exception) {}
        try { mmr.release() } catch (_: Exception) {}

        val book = BookEntity(
            infoHash = infoHash,
            title = existing?.title ?: title,
            author = existing?.author ?: author,
            narrator = null,
            coverUrl = coverPath ?: existing?.coverUrl,
            description = "Single-file audiobook in Download/Audiobooks/${file.name}",
            format = file.extension.uppercase(),
            isAbridged = false,
            totalSizeBytes = file.length(),
            totalDurationMs = duration,
            sourceUrl = file.absolutePath,
            isInLibrary = existing?.isInLibrary ?: true,
            isDownloadRequested = existing?.isDownloadRequested ?: false,
            isToRead = existing?.isToRead ?: false,
            lastAccessedAt = existing?.lastAccessedAt ?: System.currentTimeMillis()
        )
        database.bookDao().insertOrUpdate(book)

        val track = AudioTrackEntity(
            id = "${infoHash}_0",
            infoHash = infoHash,
            trackIndex = 0,
            fileName = file.name,
            fileSizeBytes = file.length(),
            durationMs = duration
        )
        database.audioTrackDao().insertTracks(listOf(track))
        Log.i(TAG, "Indexed single-file audiobook '${book.title}' (${file.length() / (1024 * 1024)} MB)")
    }

    private suspend fun findExistingBookForFile(file: File): BookEntity? {
        val track = database.audioTrackDao().findTrackByFileName(file.name)
        if (track != null) {
            val book = database.bookDao().getBook(track.infoHash)
            if (book != null && !book.infoHash.startsWith("file_") && !book.infoHash.startsWith("dir_")) {
                return book
            }
        }

        val allBooks = database.bookDao().getAllBooks()
        val cleanName = cleanTitle(file.nameWithoutExtension)
        if (cleanName.length >= 3) {
            return allBooks.firstOrNull {
                if (it.infoHash.startsWith("file_") || it.infoHash.startsWith("dir_")) return@firstOrNull false
                val cleanBook = cleanTitle(it.title)
                cleanBook.isNotEmpty() && (cleanBook.contains(cleanName) || cleanName.contains(cleanBook))
            }
        }
        return null
    }

    private suspend fun findExistingBookForDirectory(dir: File, audioFiles: List<File>): BookEntity? {
        for (f in audioFiles) {
            val track = database.audioTrackDao().findTrackByFileName(f.name)
            if (track != null) {
                val book = database.bookDao().getBook(track.infoHash)
                if (book != null && !book.infoHash.startsWith("file_") && !book.infoHash.startsWith("dir_")) {
                    return book
                }
            }
        }

        val allBooks = database.bookDao().getAllBooks()
        val cleanDir = cleanTitle(dir.name)
        if (cleanDir.length >= 3) {
            return allBooks.firstOrNull {
                if (it.infoHash.startsWith("file_") || it.infoHash.startsWith("dir_")) return@firstOrNull false
                val cleanBook = cleanTitle(it.title)
                cleanBook.isNotEmpty() && (cleanBook.contains(cleanDir) || cleanDir.contains(cleanBook))
            }
        }
        return null
    }

    private suspend fun deduplicateBooks() {
        val allTracks = database.audioTrackDao().getAllTracks()

        // 1. Deduplicate by identical audio file name
        val tracksByFile = allTracks.groupBy { File(it.fileName).name.lowercase() }
        for ((_, tracks) in tracksByFile) {
            val infoHashes = tracks.map { it.infoHash }.distinct()
            if (infoHashes.size > 1) {
                val books = infoHashes.mapNotNull { database.bookDao().getBook(it) }
                // Prefer the original ABB book (has coverUrl or does NOT start with file_/dir_)
                val primary = books.firstOrNull { !it.coverUrl.isNullOrEmpty() }
                    ?: books.firstOrNull { !it.infoHash.startsWith("file_") && !it.infoHash.startsWith("dir_") }
                    ?: books.first()

                for (b in books) {
                    if (b.infoHash != primary.infoHash) {
                        Log.i(TAG, "Deduplicating: Removing duplicate book '${b.title}' (${b.infoHash}) in favor of '${primary.title}' (${primary.infoHash})")
                        database.bookDao().deleteBook(b.infoHash)
                    }
                }
            }
        }

        // 2. Deduplicate by matching title
        val currentBooks = database.bookDao().getAllBooks()
        val booksByTitle = currentBooks.groupBy { cleanTitle(it.title) }
        for ((cleanTitle, books) in booksByTitle) {
            if (cleanTitle.isNotEmpty() && books.size > 1) {
                val primary = books.firstOrNull { !it.coverUrl.isNullOrEmpty() }
                    ?: books.firstOrNull { !it.infoHash.startsWith("file_") && !it.infoHash.startsWith("dir_") }
                    ?: books.first()

                for (b in books) {
                    if (b.infoHash != primary.infoHash) {
                        Log.i(TAG, "Deduplicating: Removing title duplicate '${b.title}' (${b.infoHash}) in favor of '${primary.title}' (${primary.infoHash})")
                        database.bookDao().deleteBook(b.infoHash)
                    }
                }
            }
        }
    }

    private fun cleanTitle(raw: String): String {
        return raw.lowercase()
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("[^a-z0-9]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isAudioFile(file: File): Boolean {
        return file.extension.lowercase() in AUDIO_EXTENSIONS
    }

    private fun generateHash(key: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
