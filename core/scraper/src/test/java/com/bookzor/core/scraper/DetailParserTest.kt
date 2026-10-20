package com.bookzor.core.scraper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailParserTest {

    private val sampleDetailHtml = """
        <div class="postTitle"><h2>Dune - Frank Herbert</h2></div>
        <div class="postContent">
            <p class="center"><img src="https://m.media-amazon.com/images/I/71-1WBgjGoL._SL1500_.jpg" itemprop="image" /></p>
        </div>
        <div class="desc" itemprop="description">
            <p>Written by <span class="author">Frank Herbert</span></p>
            <p>Read by <span class="narrator">Scott Brick</span>, <span class="narrator">Orlagh Cassidy</span></p>
            <p>Format: <span class="format">M4B</span></p>
            <p><span class="is_abridged">Unabridged</span></p>
            <p>Bit Rate: 125 kbps Length: 21 hours and 2 minutes</p>
            <p>Set on the desert planet Arrakis, Dune is the story of Paul Atreides...</p>
        </div>
        <table>
            <tr><td style="width:150px;">Announce URL:</td><td>http://bt.okmp3.ru:2710/announce</td></tr>
            <tr><td>Tracker:</td><td>udp://tracker.opentrackr.org:1337/announce</td></tr>
            <tr><td colspan='2'>B000R34YKC.m4b 1.12 GBs</td></tr>
            <tr><td>File Size:</td><td>1.12 GBs</td></tr>
            <tr><td>Piece Size:</td><td>512 KBs</td></tr>
            <tr><td>Info Hash:</td><td>061850ead3eb6f1c5c6d8420211b4bbf2d4ee3e2</td></tr>
        </table>
    """.trimIndent()

    @Test
    fun testParseDetailPage() {
        val detail = DetailParser.parse(sampleDetailHtml, "https://audiobookbay.lu/abss/dune-frank-herbert-2/")

        assertEquals("Dune - Frank Herbert", detail.title)
        assertEquals("Frank Herbert", detail.author)
        assertEquals("Scott Brick, Orlagh Cassidy", detail.narrator)
        assertEquals("M4B", detail.format)
        assertEquals(false, detail.isAbridged)
        assertEquals("061850ead3eb6f1c5c6d8420211b4bbf2d4ee3e2", detail.infoHash)
        assertEquals("1.12 GBs", detail.fileSizeText)
        assertEquals(1, detail.tracks.size)
        assertEquals("B000R34YKC.m4b", detail.tracks[0].fileName)
        assertTrue(detail.magnetUri.contains("061850ead3eb6f1c5c6d8420211b4bbf2d4ee3e2"))
        assertTrue(detail.trackers.contains("http://bt.okmp3.ru:2710/announce"))
        assertTrue(detail.trackers.contains("udp://tracker.opentrackr.org:1337/announce"))
    }
}
