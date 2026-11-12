package com.bookzor.core.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class DomainManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build(),
    private val preferencesRepository: com.bookzor.core.common.UserPreferencesRepository? = null
) {
    companion object {
        val DEFAULT_DOMAINS = listOf(
            "https://audiobookbay.lu",
            "https://audiobookbay.is",
            "https://audiobookbay.nl",
            "https://audiobookbay.se",
            "https://audiobookbay.fi",
            "https://audiobookbay.ws"
        )
    }

    private val currentDomain = AtomicReference(
        preferencesRepository?.domainMirror?.value ?: DEFAULT_DOMAINS.first()
    )

    fun getActiveDomain(): String = currentDomain.get()

    fun setDomain(domain: String) {
        val clean = domain.trimEnd('/')
        currentDomain.set(clean)
        preferencesRepository?.setDomainMirror(clean)
    }

    suspend fun findHealthyDomain(): String = withContext(Dispatchers.IO) {
        for (candidate in DEFAULT_DOMAINS) {
            try {
                val request = Request.Builder()
                    .url(candidate)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 12; SM-T505) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .head()
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful || response.code in 200..399) {
                    currentDomain.set(candidate)
                    preferencesRepository?.setDomainMirror(candidate)
                    return@withContext candidate
                }
            } catch (_: Exception) {
                // Try next mirror
            }
        }
        currentDomain.get()
    }
}
