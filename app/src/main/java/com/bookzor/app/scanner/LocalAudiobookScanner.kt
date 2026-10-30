package com.bookzor.app.scanner

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import com.bookzor.core.database.BookzorDatabase
import com.bookzor.core.database.entity.AudioTrackEntity
import com.bookzor.core.database.entity.BookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
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
        if (!storageDir.exists() || !storageDir.isDirectory) {
            Log.d(TAG, "Storage directory not ready: ${storageDir.absolutePath}")
            return@withContext
        }

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
    }

    private suspend fun scanBookDirectory(dir: File) {
        val audioFiles = dir.walkTopDown()
            .filter { it.isFile && isAudioFile(it) }
            .sortedBy { it.name.lowercase() }
            .toList()

        if (audioFiles.isEmpty()) return

        val bookTitle = dir.name

        // Check if an existing book in the database matches this directory name
        val allBooks = database.bookDao().observeAllBooks().firstOrNull() ?: emptyList()
        val matchingBook = allBooks.firstOrNull { 
            it.title.equals(bookTitle, ignoreCase = true) || it.infoHash.equals(bookTitle, ignoreCase = true)
        }

        val infoHash = matchingBook?.infoHash ?: generateHash("dir_${dir.name.lowercase()}")
        val existing = database.bookDao().getBook(infoHash)

        var author = matchingBook?.author ?: "Local Library"
        var coverPath: String? = matchingBook?.coverUrl
        var totalDuration = 0L

        // Check if a cover image already exists in the folder
        val localCover = dir.listFiles()?.firstOrNull {
            it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp")
        }
        if (localCover != null) {
            coverPath = localCover.absolutePath
        }

        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(audioFiles.first().absolutePath)
            if (author == "Local Library") {
                author = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    ?: "Local Library"
            }

            if (coverPath == null) {
                val pic = mmr.embeddedPicture
                if (pic != null) {
                    val coverFile = File(dir, ".cover.jpg")
                    FileOutputStream(coverFile).use { it.write(pic) }
                    coverPath = coverFile.absolutePath
                }
            }
        } catch (_: Exception) {}

        for (f in audioFiles) {
            try {
                mmr.setDataSource(f.absolutePath)
                val d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                totalDuration += d
            } catch (_: Exception) {}
        }
        try { mmr.release() } catch (_: Exception) {}

        val totalSize = audioFiles.sumOf { it.length() }
        val format = audioFiles.first().extension.uppercase()

        val book = BookEntity(
            infoHash = infoHash,
            title = matchingBook?.title ?: existing?.title ?: bookTitle,
            author = author,
            narrator = matchingBook?.narrator ?: existing?.narrator,
            coverUrl = coverPath ?: existing?.coverUrl,
            description = matchingBook?.description ?: existing?.description ?: "Audiobook in Download/Audiobooks/${dir.name}",
            format = format,
            isAbridged = matchingBook?.isAbridged ?: false,
            totalSizeBytes = totalSize,
            totalDurationMs = if (totalDuration > 0) totalDuration else (matchingBook?.totalDurationMs ?: 0L),
            sourceUrl = dir.absolutePath,
            isInLibrary = true,
            isDownloadRequested = false,
            isToRead = false,
            lastAccessedAt = existing?.lastAccessedAt ?: System.currentTimeMillis()
        )
        database.bookDao().insertOrUpdate(book)

        val trackEntities = audioFiles.mapIndexed { idx, file ->
            val relPath = file.relativeTo(storageDir).path
            AudioTrackEntity(
                id = "${infoHash}_$idx",
                infoHash = infoHash,
                trackIndex = idx,
                fileName = relPath,
                fileSizeBytes = file.length(),
                durationMs = 0L
            )
        }
        database.audioTrackDao().insertTracks(trackEntities)
        Log.i(TAG, "Indexed audiobook '${book.title}' with ${trackEntities.size} tracks (${totalSize / (1024 * 1024)} MB)")
    }

    private suspend fun scanSingleAudioFile(file: File) {
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
            isInLibrary = true,
            isDownloadRequested = false,
            isToRead = false,
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

    private fun isAudioFile(file: File): Boolean {
        return file.extension.lowercase() in AUDIO_EXTENSIONS
    }

    private fun generateHash(key: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
