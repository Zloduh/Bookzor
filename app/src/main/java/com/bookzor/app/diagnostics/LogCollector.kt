package com.bookzor.app.diagnostics

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.bookzor.core.common.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object LogCollector {

    suspend fun collectAndShareLogs(context: Context) = withContext(Dispatchers.IO) {
        try {
            val logDir = File(context.cacheDir, "logs").apply { if (!exists()) mkdirs() }
            val timeFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val timestamp = timeFormat.format(Date())
            val textFile = File(logDir, "bookzor_diagnostic_$timestamp.txt")
            val zipFile = File(logDir, "bookzor_logs_$timestamp.zip")

            // 1. Build diagnostic content
            val sb = StringBuilder()
            sb.append("=========================================\n")
            sb.append("BOOKZOR DIAGNOSTIC LOG DUMP\n")
            sb.append("Timestamp: ${Date()}\n")
            sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n")
            sb.append("Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
            sb.append("App Version: 1.0.0\n")

            try {
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val freeGb = downloads.usableSpace.toDouble() / (1024 * 1024 * 1024)
                val totalGb = downloads.totalSpace.toDouble() / (1024 * 1024 * 1024)
                sb.append("Storage (Download): ${"%.2f".format(freeGb)} GB free / ${"%.2f".format(totalGb)} GB total\n")
            } catch (_: Exception) {}

            try {
                val isManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else {
                    true
                }
                sb.append("All-Files Access Granted: $isManager\n")
            } catch (_: Exception) {}

            sb.append("Theme: ${ThemeManager.currentTheme.value.displayName}\n")
            sb.append("=========================================\n\n")

            // 2. Append latest crash dump if present
            val crashFile = File(context.filesDir, "latest_crash.txt")
            if (crashFile.exists()) {
                sb.append("-----------------------------------------\n")
                sb.append("LATEST RECORDED CRASH DUMP:\n")
                sb.append("-----------------------------------------\n")
                sb.append(crashFile.readText())
                sb.append("\n\n")
            } else {
                sb.append("No unhandled crashes recorded.\n\n")
            }

            // 3. Capture recent logcat output
            sb.append("-----------------------------------------\n")
            sb.append("RECENT LOGCAT OUTPUT:\n")
            sb.append("-----------------------------------------\n")
            try {
                val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "-t", "500"))
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line).append("\n")
                    }
                }
            } catch (e: Exception) {
                sb.append("Failed to capture logcat: ${e.message}\n")
            }

            // Write raw text file
            textFile.writeText(sb.toString())

            // 4. Zip the log file (compresses by ~90%!)
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                FileInputStream(textFile).use { fis ->
                    val entry = ZipEntry(textFile.name)
                    zos.putNextEntry(entry)
                    fis.copyTo(zos)
                    zos.closeEntry()
                }
            }
            textFile.delete() // Clean up uncompressed text file

            // 5. Open Android Share Sheet
            withContext(Dispatchers.Main) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    zipFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Bookzor Logs - ${Build.MODEL}")
                    putExtra(Intent.EXTRA_TEXT, "Bookzor diagnostic log archive (${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE})")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(shareIntent, "Share Diagnostic Logs").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error exporting logs: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
