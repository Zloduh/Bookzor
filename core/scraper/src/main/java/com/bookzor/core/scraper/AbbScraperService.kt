package com.bookzor.core.scraper

import com.bookzor.core.common.Resource
import com.bookzor.core.scraper.model.BookDetail
import com.bookzor.core.scraper.model.BookSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class AbbScraperService(
    val domainManager: DomainManager = DomainManager(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {

    private val userAgent = "Mozilla/5.0 (Linux; Android 12; SM-T505) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    suspend fun search(query: String, page: Int = 1): Resource<List<BookSearchResult>> = withContext(Dispatchers.IO) {
        val domain = domainManager.getActiveDomain()
        // ABB backend strictly requires lowercase query strings for multi-word author & title searches
        val clean = query.trim().lowercase()
        val encodedQuery = URLEncoder.encode(clean, StandardCharsets.UTF_8.name())
        // tt=1 instructs ABB to perform a full title/author search
        val url = if (page <= 1) {
            "$domain/?s=$encodedQuery&tt=1"
        } else {
            "$domain/page/$page/?s=$encodedQuery&tt=1"
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Resource.Error("HTTP Error ${response.code}: ${response.message}")
            }

            val body = response.body?.string() ?: return@withContext Resource.Error("Empty response body from ABB")
            val results = SearchParser.parse(body, domain)
            Resource.Success(results)
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.localizedMessage ?: e.message}", e)
        } catch (e: Exception) {
            Resource.Error("Scraper error: ${e.localizedMessage ?: e.message}", e)
        }
    }

    suspend fun fetchBookDetail(detailUrl: String): Resource<BookDetail> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(detailUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Resource.Error("HTTP Error ${response.code}: ${response.message}")
            }

            val body = response.body?.string() ?: return@withContext Resource.Error("Empty response body from ABB")
            val detail = DetailParser.parse(body, detailUrl)
            Resource.Success(detail)
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.localizedMessage ?: e.message}", e)
        } catch (e: Exception) {
            Resource.Error("Scraper error: ${e.localizedMessage ?: e.message}", e)
        }
    }
}
