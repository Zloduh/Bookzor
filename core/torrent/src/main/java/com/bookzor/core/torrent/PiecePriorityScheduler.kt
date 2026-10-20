package com.bookzor.core.torrent

import org.libtorrent4j.Priority
import org.libtorrent4j.TorrentHandle

class PiecePriorityScheduler(
    private val handle: TorrentHandle
) {
    private val pieceLength: Long
        get() {
            val info = if (handle.isValid) handle.torrentFile() else null
            return if (info != null && info.isValid) info.pieceLength().toLong() else 0L
        }

    private val totalPieces: Int
        get() {
            val info = if (handle.isValid) handle.torrentFile() else null
            return if (info != null && info.isValid) info.numPieces() else 0
        }

    /**
     * Prioritize pieces for M4B metadata: first 2 pieces (header) and last 2 pieces (moov atom)
     */
    fun prioritizeContainerHeaders() {
        val pieces = totalPieces
        if (pieces <= 0 || !handle.isValid) return

        try {
            // First pieces (headers)
            for (i in 0 until minOf(3, pieces)) {
                handle.piecePriority(i, Priority.TOP_PRIORITY)
                handle.setPieceDeadline(i, 500)
            }

            // Last pieces (trailer/moov box)
            for (i in maxOf(0, pieces - 3) until pieces) {
                handle.piecePriority(i, Priority.TOP_PRIORITY)
                handle.setPieceDeadline(i, 500)
            }
        } catch (_: Exception) {
            // Ignore transient JNI errors while metadata initializes
        }
    }

    /**
     * Set active playhead offset in bytes.
     * Escalates piece priority around the playhead and in the sequential read-ahead window.
     */
    fun onSeek(byteOffset: Long, windowSizePieces: Int = 15) {
        val pLen = pieceLength
        val pieces = totalPieces
        if (pLen <= 0 || pieces <= 0 || !handle.isValid) return

        try {
            val currentPiece = (byteOffset / pLen).toInt().coerceIn(0, pieces - 1)
            val endWindow = (currentPiece + windowSizePieces).coerceAtMost(pieces)

            // Immediate pieces around playhead: TOP_PRIORITY (7)
            val topPriorityEnd = (currentPiece + 3).coerceAtMost(pieces)
            for (i in currentPiece until topPriorityEnd) {
                handle.piecePriority(i, Priority.TOP_PRIORITY)
                handle.setPieceDeadline(i, 1000)
            }

            // Next read-ahead pieces: DEFAULT / HIGH priority
            for (i in topPriorityEnd until endWindow) {
                handle.piecePriority(i, Priority.DEFAULT)
            }
        } catch (_: Exception) {
            // Ignore transient errors
        }
    }
}
