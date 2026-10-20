package com.bookzor.core.scraper

import com.bookzor.core.scraper.model.AudioTrackInfo
import com.bookzor.core.scraper.model.BookDetail
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object DetailParser {

    fun parse(html: String, detailUrl: String): BookDetail {
        val doc: Document = Jsoup.parse(html, detailUrl)

        // Title
        val title = doc.selectFirst("div.postTitle h1, div.postTitle h2")?.text()?.trim()
            ?: doc.selectFirst("title")?.text()?.substringBefore("Audiobook")?.trim()
            ?: "Unknown Title"

        // Cover
        val coverImg = doc.selectFirst("img[itemprop=image], div.postContent img")
        val coverUrl = coverImg?.attr("src")?.trim()

        // Author
        val author = doc.select("span.author").joinToString(", ") { it.text().trim() }
            .ifEmpty { "Unknown Author" }

        // Narrator
        val narrator = doc.select("span.narrator").joinToString(", ") { it.text().trim() }
            .ifEmpty { null }

        // Format & Abridged
        val format = doc.selectFirst("span.format")?.text()?.trim()?.uppercase() ?: "MP3"
        val isAbridged = doc.selectFirst("span.is_abridged")?.text()?.contains("Abridged", ignoreCase = true) == true
                && !doc.selectFirst("span.is_abridged")!!.text().contains("Unabridged", ignoreCase = true)

        // Description
        val descEl = doc.selectFirst("div.desc")
        val description = descEl?.text()?.trim() ?: ""

        // Extract metadata from table rows
        var infoHash = ""
        var fileSizeText = ""
        var pieceSizeText: String? = null
        val trackers = mutableListOf<String>()
        val tracks = mutableListOf<AudioTrackInfo>()

        var trackIndex = 0

        val rows = doc.select("table tr")
        for (row in rows) {
            val cells = row.select("td")
            if (cells.size == 2) {
                val label = cells[0].text().trim()
                val value = cells[1].text().trim()

                when {
                    label.equals("Info Hash:", ignoreCase = true) -> infoHash = value
                    label.equals("File Size:", ignoreCase = true) -> fileSizeText = value
                    label.equals("Piece Size:", ignoreCase = true) -> pieceSizeText = value
                    label.equals("Tracker:", ignoreCase = true) || label.equals("Announce URL:", ignoreCase = true) -> {
                        if (value.startsWith("http") || value.startsWith("udp")) {
                            trackers.add(value)
                        }
                    }
                }
            } else if (cells.size == 1 && cells[0].hasAttr("colspan")) {
                val text = cells[0].text().trim()
                // Track row e.g.: "B000R34YKC.m4b 1.12 GBs" or "01 - Chapter 1.mp3 45.2 MBs"
                val audioExtensions = listOf(".m4b", ".mp3", ".m4a", ".ogg", ".opus", ".flac")
                if (audioExtensions.any { text.contains(it, ignoreCase = true) }) {
                    val parts = text.split(" ")
                    val filename = parts.firstOrNull() ?: text
                    val sizeStr = parts.drop(1).joinToString(" ").ifEmpty { "Unknown" }
                    val bytes = parseBytes(sizeStr)
                    tracks.add(
                        AudioTrackInfo(
                            index = trackIndex++,
                            fileName = filename,
                            sizeString = sizeStr,
                            sizeBytes = bytes
                        )
                    )
                }
            }
        }

        // If no explicit track rows were found, create a virtual single track from the title & total size
        if (tracks.isEmpty()) {
            val extension = if (format == "M4B") ".m4b" else ".mp3"
            val fallbackName = "${title.replace(Regex("[^a-zA-Z0-9.-]"), "_")}$extension"
            tracks.add(
                AudioTrackInfo(
                    index = 0,
                    fileName = fallbackName,
                    sizeString = fileSizeText,
                    sizeBytes = parseBytes(fileSizeText)
                )
            )
        }

        // Bitrate & Duration from description
        var durationText: String? = null
        var bitrateText: String? = null
        val descText = descEl?.text() ?: ""
        if (descText.contains("Length:", ignoreCase = true)) {
            durationText = descText.substringAfter("Length:").substringBefore("\n").substringBefore("Narrator").trim()
        }
        if (descText.contains("Bit Rate:", ignoreCase = true)) {
            bitrateText = descText.substringAfter("Bit Rate:").substringBefore("\n").substringBefore("Length").trim()
        }

        val magnetUri = MagnetBuilder.build(infoHash = infoHash, title = title, trackers = trackers)

        return BookDetail(
            title = title,
            author = author,
            narrator = narrator,
            coverUrl = coverUrl,
            format = format,
            isAbridged = isAbridged,
            durationText = durationText,
            bitrateText = bitrateText,
            fileSizeText = fileSizeText,
            pieceSizeText = pieceSizeText,
            description = description,
            infoHash = infoHash,
            trackers = trackers.distinct(),
            tracks = tracks,
            magnetUri = magnetUri
        )
    }

    private fun parseBytes(sizeStr: String): Long {
        val clean = sizeStr.trim().uppercase()
        val num = Regex("([0-9.]+)").find(clean)?.value?.toDoubleOrNull() ?: return 0L
        return when {
            clean.contains("GB") -> (num * 1024 * 1024 * 1024).toLong()
            clean.contains("MB") -> (num * 1024 * 1024).toLong()
            clean.contains("KB") -> (num * 1024).toLong()
            else -> num.toLong()
        }
    }
}
