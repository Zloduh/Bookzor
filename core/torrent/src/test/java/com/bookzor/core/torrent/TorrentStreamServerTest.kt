package com.bookzor.core.torrent

import org.junit.Assert.assertEquals
import org.junit.Test

class TorrentStreamServerTest {

    @Test
    fun testGetStreamUrl() {
        val hash = "061850EAD3EB6F1C5C6D8420211B4BBF2D4EE3E2"
        val expected = "http://127.0.0.1:8080/stream?hash=061850ead3eb6f1c5c6d8420211b4bbf2d4ee3e2&fileIndex=0"

        val generated = "http://127.0.0.1:8080/stream?hash=${hash.lowercase()}&fileIndex=0"

        assertEquals(expected, generated)
    }
}
