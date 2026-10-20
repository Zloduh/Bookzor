package com.bookzor.core.scraper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MagnetBuilderTest {

    @Test
    fun testBuildMagnetWithTrackers() {
        val hash = "061850EAD3EB6F1C5C6D8420211B4BBF2D4EE3E2"
        val title = "Dune - Frank Herbert"
        val trackers = listOf("http://bt.okmp3.ru:2710/announce", "udp://tracker.opentrackr.org:1337/announce")

        val magnet = MagnetBuilder.build(hash, title, trackers)

        assertTrue(magnet.startsWith("magnet:?xt=urn:btih:061850ead3eb6f1c5c6d8420211b4bbf2d4ee3e2"))
        assertTrue(magnet.contains("&dn=Dune+-+Frank+Herbert") || magnet.contains("&dn=Dune%20-%20Frank%20Herbert"))
        assertTrue(magnet.contains("tr=http%3A%2F%2Fbt.okmp3.ru%3A2710%2Fannounce"))
        assertTrue(magnet.contains("tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce"))
    }

    @Test
    fun testBuildMagnetEmptyTrackersAppendsFallbacks() {
        val hash = "061850ead3eb6f1c5c6d8420211b4bbf2d4ee3e2"
        val title = "Dune"

        val magnet = MagnetBuilder.build(hash, title, emptyList())

        assertTrue(magnet.contains("tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce"))
    }
}
