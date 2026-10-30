package com.bookzor.app.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionName: String,
    val releaseTitle: String,
    val changelog: String,
    val downloadUrl: String,
    val sizeBytes: Long
)

sealed class UpdateStatus {
    data object Idle : UpdateStatus()
    data object Checking : UpdateStatus()
    data class Available(val updateInfo: UpdateInfo) : UpdateStatus()
    data object UpToDate : UpdateStatus()
    data class Downloading(val progress: Float, val downloadedMb: Float, val totalMb: Float) : UpdateStatus()
    data class ReadyToInstall(val apkFile: File) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

class AppUpdateManager(
    private val context: Context,
    val currentVersion: String = "1.0.0"
) {
    companion object {
        private const val GITHUB_LATEST_RELEASE_URL =
            "https://api.github.com/repos/Zloduh/bookzor-releases/releases/latest"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    suspend fun checkForUpdates(): UpdateStatus = withContext(Dispatchers.IO) {
        _status.value = UpdateStatus.Checking
        try {
            val request = Request.Builder()
                .url(GITHUB_LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Bookzor-App")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorMsg = if (response.code == 404) "No releases published yet." else "Server returned HTTP ${response.code}"
                val errorState = UpdateStatus.Error(errorMsg)
                _status.value = errorState
                return@withContext errorState
            }

            val body = response.body?.string() ?: throw IllegalStateException("Empty response")
            val json = JSONObject(body)

            val tagName = json.optString("tag_name", "").removePrefix("v")
            val title = json.optString("name", "Version $tagName")
            val changelog = json.optString("body", "Bug fixes and improvements.")

            val assets = json.optJSONArray("assets") ?: org.json.JSONArray()
            var apkUrl: String? = null
            var apkSize = 0L

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    apkSize = asset.optLong("size", 0L)
                    break
                }
            }

            if (apkUrl == null) {
                val upToDate = UpdateStatus.UpToDate
                _status.value = upToDate
                return@withContext upToDate
            }

            val isNewer = isVersionNewer(tagName, currentVersion)
            val result = if (isNewer) {
                UpdateStatus.Available(
                    UpdateInfo(
                        versionName = tagName,
                        releaseTitle = title,
                        changelog = changelog,
                        downloadUrl = apkUrl,
                        sizeBytes = apkSize
                    )
                )
            } else {
                UpdateStatus.UpToDate
            }

            _status.value = result
            result
        } catch (e: Exception) {
            val error = UpdateStatus.Error(e.message ?: "Failed to check for updates")
            _status.value = error
            error
        }
    }

    suspend fun downloadAndInstall(updateInfo: UpdateInfo) = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { if (!exists()) mkdirs() }
            val apkFile = File(updatesDir, "Bookzor-${updateInfo.versionName}.apk")

            val request = Request.Builder()
                .url(updateInfo.downloadUrl)
                .header("User-Agent", "Bookzor-App")
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body ?: throw IllegalStateException("No download body")
            val contentLength = responseBody.contentLength().let { if (it > 0) it else updateInfo.sizeBytes }

            responseBody.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var totalRead = 0L
                    var read: Int

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        totalRead += read
                        if (contentLength > 0) {
                            val progress = totalRead.toFloat() / contentLength
                            val dlMb = totalRead.toFloat() / (1024 * 1024)
                            val totalMb = contentLength.toFloat() / (1024 * 1024)
                            _status.value = UpdateStatus.Downloading(progress, dlMb, totalMb)
                        }
                    }
                    output.flush()
                }
            }

            _status.value = UpdateStatus.ReadyToInstall(apkFile)

            withContext(Dispatchers.Main) {
                promptInstall(apkFile)
            }
        } catch (e: Exception) {
            _status.value = UpdateStatus.Error(e.message ?: "Download failed")
        }
    }

    fun promptInstall(apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            }
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(installIntent)
    }

    fun resetStatus() {
        _status.value = UpdateStatus.Idle
    }

    private fun isVersionNewer(remote: String, current: String): Boolean {
        val rParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val cParts = current.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(rParts.size, cParts.size)
        for (i in 0 until length) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
