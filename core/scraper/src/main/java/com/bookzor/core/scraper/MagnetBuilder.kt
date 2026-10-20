package com.bookzor.core.scraper

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object MagnetBuilder {

    val FALLBACK_TRACKERS = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://explodie.org:6969/announce",
        "udp://tracker.openbittorrent.com:6969/announce",
        "http://tracker.openbittorrent.com:80/announce"
    )

    fun build(infoHash: String, title: String, trackers: List<String> = emptyList()): String {
        val cleanHash = infoHash.trim().lowercase()
        val encodedTitle = URLEncoder.encode(title.trim(), StandardCharsets.UTF_8.name())

        // Merge given trackers with fallback trackers, deduplicating
        val mergedTrackers = (trackers.map { it.trim() }.filter { it.isNotBlank() } + FALLBACK_TRACKERS)
            .distinct()

        val sb = StringBuilder()
        sb.append("magnet:?xt=urn:btih:").append(cleanHash)
        sb.append("&dn=").append(encodedTitle)

        for (tracker in mergedTrackers) {
            val encodedTracker = URLEncoder.encode(tracker, StandardCharsets.UTF_8.name())
            sb.append("&tr=").append(encodedTracker)
        }

        return sb.toString()
    }
}
