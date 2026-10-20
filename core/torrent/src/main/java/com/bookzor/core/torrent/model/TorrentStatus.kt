package com.bookzor.core.torrent.model

data class TorrentStatus(
    val infoHash: String,
    val name: String,
    val progress: Float,
    val downloadRate: Long, // bytes/sec
    val uploadRate: Long,   // bytes/sec
    val seeds: Int,
    val peers: Int,
    val state: State,
    val pieceCount: Int,
    val pieces: BooleanArray
) {
    enum class State {
        CHECKING,
        DOWNLOADING_METADATA,
        DOWNLOADING,
        FINISHED,
        SEEDING,
        ALLOCATING,
        PAUSED,
        ERROR
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TorrentStatus
        return infoHash == other.infoHash &&
                name == other.name &&
                progress == other.progress &&
                downloadRate == other.downloadRate &&
                seeds == other.seeds &&
                peers == other.peers &&
                state == other.state &&
                pieceCount == other.pieceCount &&
                pieces.contentEquals(other.pieces)
    }

    override fun hashCode(): Int {
        var result = infoHash.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + progress.hashCode()
        result = 31 * result + downloadRate.hashCode()
        result = 31 * result + seeds
        result = 31 * result + peers
        result = 31 * result + state.hashCode()
        result = 31 * result + pieceCount
        result = 31 * result + pieces.contentHashCode()
        return result
    }
}
