package com.bookzor.core.torrent

import org.libtorrent4j.Priority
import org.libtorrent4j.TorrentHandle

class PiecePriorityScheduler(
    private val handle: TorrentHandle
) {
    private val pieceLength: Long
    private val totalPieces: Int

    init {
        val info = handle.torrentFile()
        pieceLength = info.pieceLength().toLong()
        totalPieces = info.numPieces()
    }

    /**
     * Prioritize pieces for M4B metadata: first 2 pieces (header) and last 2 pieces (moov atom)
     */
    fun prioritizeContainerHeaders() {
        if (totalPieces <= 0) return

        // First pieces (headers)
        for (i in 0 until minOf(3, totalPieces)) {
            handle.piecePriority(i, Priority.TOP_PRIORITY)
            handle.setPieceDeadline(i, 500)
        }

        // Last pieces (trailer/moov box)
        for (i in maxOf(0, totalPieces - 3) until totalPieces) {
            handle.piecePriority(i, Priority.TOP_PRIORITY)
            handle.setPieceDeadline(i, 500)
        }
    }

    /**
     * Set active playhead offset in bytes.
     * Escalates piece priority around the playhead and in the sequential read-ahead window.
     */
    fun onSeek(byteOffset: Long, windowSizePieces: Int = 15) {
        if (pieceLength <= 0 || totalPieces <= 0) return

        val currentPiece = (byteOffset / pieceLength).toInt().coerceIn(0, totalPieces - 1)
        val endWindow = (currentPiece + windowSizePieces).coerceAtMost(totalPieces)

        // Immediate pieces around playhead: TOP_PRIORITY (7)
        val topPriorityEnd = (currentPiece + 3).coerceAtMost(totalPieces)
        for (i in currentPiece until topPriorityEnd) {
            handle.piecePriority(i, Priority.TOP_PRIORITY)
            handle.setPieceDeadline(i, 1000)
        }

        // Next read-ahead pieces: DEFAULT / HIGH priority
        for (i in topPriorityEnd until endWindow) {
            handle.piecePriority(i, Priority.DEFAULT)
        }
    }
}
