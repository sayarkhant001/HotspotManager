package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppReleaseInfo(
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val apkSize: Long = 0L,
    val isNewer: Boolean = false
)

object GitHubUpdateManager {

    // Repositories to check: primary requested by user, with fallback
    private val REPO_URLS = listOf(
        "https://api.github.com/repos/sayarkhant001/mikrotik-Manager/releases/latest",
        "https://api.github.com/repos/sayarkhant001/HotspotManager/releases/latest"
    )

    suspend fun checkForUpdate(): AppReleaseInfo? = withContext(Dispatchers.IO) {
        for (apiUrl in REPO_URLS) {
            try {
                val url = URL(apiUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "HotspotManager-Android/${BuildConfig.VERSION_NAME}")
                }

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseText)

                    val tagName = json.optString("tag_name", "").trim()
                    val cleanVersion = tagName.removePrefix("v").removePrefix("V").trim()
                    val title = json.optString("name", tagName)
                    val body = json.optString("body", "No release notes provided.")

                    // Search for .apk asset
                    var apkUrl = ""
                    var apkSize = 0L
                    val assets = json.optJSONArray("assets") ?: JSONArray()
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val assetName = asset.optString("name", "")
                        if (assetName.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", "")
                            apkSize = asset.optLong("size", 0L)
                            break
                        }
                    }

                    if (apkUrl.isBlank()) {
                        // If no APK asset uploaded directly, fall back to release html page
                        apkUrl = json.optString("html_url", "")
                    }

                    val currentVersion = BuildConfig.VERSION_NAME.removePrefix("v").removePrefix("V").trim()
                    val isNewer = isVersionNewer(cleanVersion, currentVersion)

                    return@withContext AppReleaseInfo(
                        versionName = cleanVersion.ifBlank { tagName },
                        releaseTitle = title.ifBlank { "Version $cleanVersion" },
                        releaseNotes = body,
                        downloadUrl = apkUrl,
                        apkSize = apkSize,
                        isNewer = isNewer
                    )
                }
            } catch (e: Exception) {
                // Try next repository fallback
                e.printStackTrace()
            }
        }
        null
    }

    private fun isVersionNewer(remote: String, current: String): Boolean {
        if (remote.isBlank() || current.isBlank()) return false
        if (remote.equals(current, ignoreCase = true)) return false

        try {
            val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
            val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
        } catch (_: Exception) {}

        return remote != current
    }

    suspend fun downloadAndInstallApk(
        context: Context,
        release: AppReleaseInfo,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (release.downloadUrl.isBlank() || !release.downloadUrl.endsWith(".apk", ignoreCase = true)) {
                // Fallback to opening browser if no direct APK asset
                launchBrowser(context, release.downloadUrl)
                return@withContext Result.success(Unit)
            }

            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.cacheDir
            if (!downloadDir.exists()) downloadDir.mkdirs()

            val apkFile = File(downloadDir, "HotspotManager-v${release.versionName}.apk")
            if (apkFile.exists()) apkFile.delete()

            val url = URL(release.downloadUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("User-Agent", "HotspotManager-Android")
            }

            // Handle HTTP redirects (GitHub releases redirect to AWS S3/fastly CDN)
            var actualConn = connection
            var redirectCount = 0
            while (actualConn.responseCode in listOf(301, 302, 303, 307, 308) && redirectCount < 5) {
                val newUrl = actualConn.getHeaderField("Location")
                actualConn.disconnect()
                actualConn = (URL(newUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15000
                    readTimeout = 20000
                    setRequestProperty("User-Agent", "HotspotManager-Android")
                }
                redirectCount++
            }

            val totalBytes = actualConn.contentLengthLong
            var downloadedBytes = 0L

            actualConn.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            val progress = downloadedBytes.toFloat() / totalBytes.toFloat()
                            onProgress(progress.coerceIn(0f, 1f))
                        }
                    }
                    output.flush()
                }
            }

            onProgress(1f)

            // Launch package installer on Main Thread
            withContext(Dispatchers.Main) {
                installApkFile(context, apkFile, release.downloadUrl)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            // Graceful fallback to opening GitHub in browser
            withContext(Dispatchers.Main) {
                launchBrowser(context, release.downloadUrl)
            }
            Result.failure(e)
        }
    }

    private fun installApkFile(context: Context, apkFile: File, fallbackUrl: String) {
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            launchBrowser(context, fallbackUrl)
        }
    }

    fun launchBrowser(context: Context, url: String) {
        try {
            val targetUrl = if (url.isNotBlank()) url else "https://github.com/sayarkhant001/mikrotik-Manager/releases"
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        } catch (_: Exception) {}
    }
}
