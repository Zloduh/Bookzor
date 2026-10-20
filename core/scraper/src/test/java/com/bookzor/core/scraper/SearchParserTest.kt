package com.bookzor.core.scraper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SearchParserTest {

    private val sampleSearchHtml = """
        <div class="post">
            <div class="postTitle"><h2><a href="/abss/dune-frank-herbert-2/" rel="bookmark">Dune - Frank Herbert</a></h2></div>
            <div class="postInfo">Category: Adults&nbsp; Classic&nbsp; Sci-Fi&nbsp; <br />Language: English<span style="margin-left:100px;">Keywords: Epic Fantasy&nbsp; Space Opera&nbsp; </span><br /></div>
            <div class="postContent">
                <div class="center">
                    <p class="center"><a href="https://audiobookbay.lu/abss/dune-frank-herbert-2/"><img src="https://m.media-amazon.com/images/I/71-1WBgjGoL._SL1500_.jpg" alt="Frank Herbert Dune" width="250" /></a></p>
                </div>
            </div>
        </div>
    """.trimIndent()

    @Test
    fun testParseSearchResults() {
        val results = SearchParser.parse(sampleSearchHtml, "https://audiobookbay.lu")

        assertEquals(1, results.size)
        val first = results[0]
        assertEquals("Dune - Frank Herbert", first.title)
        assertEquals("https://audiobookbay.lu/abss/dune-frank-herbert-2/", first.detailUrl)
        assertEquals("https://m.media-amazon.com/images/I/71-1WBgjGoL._SL1500_.jpg", first.coverUrl)
        assertEquals("English", first.language)
        assertEquals(listOf("Adults", "Classic", "Sci-Fi"), first.categories)
    }
}
