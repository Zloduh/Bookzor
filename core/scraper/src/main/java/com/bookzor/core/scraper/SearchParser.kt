package com.bookzor.core.scraper

import com.bookzor.core.scraper.model.BookSearchResult
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object SearchParser {

    fun parse(html: String, baseUrl: String): List<BookSearchResult> {
        val doc: Document = Jsoup.parse(html, baseUrl)
        val postElements = doc.select("div.post")
        val results = mutableListOf<BookSearchResult>()

        for (post in postElements) {
            val titleLink = post.selectFirst("div.postTitle h2 a") ?: continue
            val title = titleLink.text().trim()
            val href = titleLink.attr("href").trim()
            val detailUrl = if (href.startsWith("http")) href else "$baseUrl$href"

            // Cover Image
            val img = post.selectFirst("div.postContent img")
            var coverUrl = img?.attr("src")?.trim()
            if (coverUrl != null && !coverUrl.startsWith("http")) {
                coverUrl = "$baseUrl$coverUrl"
            }

            // Categories
            val postInfo = post.selectFirst("div.postInfo")?.text() ?: ""
            val categories = mutableListOf<String>()
            if (postInfo.contains("Category:")) {
                val catPart = postInfo.substringAfter("Category:").substringBefore("Language:").trim()
                categories.addAll(catPart.split(Regex("[\\s,;]+")).filter { it.isNotBlank() })
            }

            // Language
            var language: String? = null
            if (postInfo.contains("Language:")) {
                language = postInfo.substringAfter("Language:").substringBefore("Keywords:").trim()
            }

            if (title.isNotEmpty()) {
                results.add(
                    BookSearchResult(
                        title = title,
                        detailUrl = detailUrl,
                        coverUrl = coverUrl,
                        categories = categories,
                        language = language
                    )
                )
            }
        }

        return results
    }
}
